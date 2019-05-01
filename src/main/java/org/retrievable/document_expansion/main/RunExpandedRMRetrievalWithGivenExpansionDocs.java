package org.retrievable.document_expansion.main;

import edu.gslis.docscoring.support.CollectionStats;
import edu.gslis.docscoring.support.IndexBackedCollectionStats;
import edu.gslis.indexes.CachedFeatureVectorIndexWrapperIndriImpl;
import edu.gslis.indexes.IndexWrapper;
import edu.gslis.indexes.IndexWrapperIndriImpl;
import edu.gslis.output.FormattedOutputTrecEval;
import edu.gslis.queries.GQueries;
import edu.gslis.queries.GQueriesFactory;
import edu.gslis.queries.GQuery;
import edu.gslis.scoring.expansion.ExpandedRM1Builder;
import edu.gslis.scoring.expansion.ProgressiveExpandedRM1Builder;
import edu.gslis.scoring.expansion.RM3Builder;
import edu.gslis.searchhits.IndexBackedSearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.textrepresentation.FeatureVector;
import edu.gslis.utils.Stopper;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.csv.QuoteMode;
import org.retrievable.documentExpansion.utils.OptimalParameters;
import org.retrievable.document_expansion.expansion.DocumentExpander;
import org.retrievable.document_expansion.expansion.DocumentExpanderWithPremadePseudoQueries;
import org.retrievable.document_expansion.expansion.PreExpandedDocumentExpander;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class RunExpandedRMRetrievalWithGivenExpansionDocs {

	public static void main(String[] args) throws ConfigurationException {
		// Load configuration
		Configuration config = new PropertiesConfiguration(args[0]);

		//int numTerms = Integer.parseInt(args[1]);
		String queryName = args[1];
        String paramsFile = args[2];
        OptimalParameters expansionParams = new OptimalParameters(new File(paramsFile), queryName);

        // going to assume the files are named collection_vectorSize. you should pass in the path up to collection, and then
        // _vectorSize will be added automatically as needed
		String expansionDocsFile = args[3] + "_" + expansionParams.getVecSize();

		// Load resources
		Stopper stopper = new Stopper(config.getString("stoplist"));

		IndexWrapper targetIndex = new CachedFeatureVectorIndexWrapperIndriImpl(config.getString("target-index"));
        List<IndexWrapper> expansionIndexes = Arrays.stream(config.getStringArray("expansion-index"))
                .map(CachedFeatureVectorIndexWrapperIndriImpl::new)
                .collect(Collectors.toList());

		GQueries queries = GQueriesFactory.getGQueries(config.getString("queries"));
		GQuery query = queries.getNamedQuery(queryName);

		CollectionStats targetCollectionStats = new IndexBackedCollectionStats();
		targetCollectionStats.setStatSource(config.getString("target-index"));

		int minFbDocs = Integer.parseInt(config.getString("min-fbdocs", "10"));
		int maxFbDocs = Integer.parseInt(config.getString("max-fbdocs", "50"));
		int fbDocsInterval = Integer.parseInt(config.getString("fbdocs-interval", "10"));

		int minFbTerms = Integer.parseInt(config.getString("min-fbterms", "10"));
		int maxFbTerms = Integer.parseInt(config.getString("max-fbterms", "50"));
		int fbTermsInterval = Integer.parseInt(config.getString("fbterms-interval", "10"));

        // Get the feedback docs
        query.applyStopper(stopper);
        SearchHits feedbackDocs = targetIndex.runQuery(query, maxFbDocs); // get max fbDocs; we'll trim as we go along

        Map<String, SearchHits> expansionDocs = new HashMap<>();
        try {
            //Scanner scanner = new Scanner(new File(expansionDocsFile));
            Iterable<CSVRecord> records = CSVFormat.EXCEL.withHeader("origDocno", "relatedDocno", "cosine").withQuoteMode(QuoteMode.MINIMAL).parse(new FileReader(expansionDocsFile));
            //while (scanner.hasNextLine()) {
            for (CSVRecord record : records) {
                //String[] parts = scanner.nextLine().split(",");

                //String origDocno = parts[0];
                String origDocno = record.get("origDocno");

                if (!expansionDocs.containsKey(origDocno)) {
                    expansionDocs.put(origDocno, new SearchHits());
                }

                if (expansionDocs.get(origDocno).size() >= expansionParams.getNumDocs()){
                    continue;
                }

                //String relatedDocno = parts[1];
                //double cosine = Double.parseDouble(parts[2]);
                String relatedDocno = record.get("relatedDocno");
                double cosine = Double.parseDouble(record.get("cosine"));

                IndexBackedSearchHit expHit = new IndexBackedSearchHit(expansionIndexes.get(0)); // this is bad, but in reality i know there's only going to be one expansion index
                expHit.setDocno(relatedDocno);
                expHit.setScore(cosine);

                expansionDocs.get(origDocno).add(expHit);
            }
        } catch (FileNotFoundException e) {
            System.out.println("Couldn't open file: " + expansionDocsFile);
            System.exit(-1);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(-1);
        }

        List<DocumentExpander> docExpanders = expansionIndexes
                .stream()
                .map(expansionIndex -> new PreExpandedDocumentExpander(expansionIndex, expansionParams.getNumTerms(), stopper, expansionDocs))
                .collect(Collectors.toList());

		// Prep RM builders
        ExpandedRM1Builder rm1Builder = new ExpandedRM1Builder(maxFbDocs, maxFbTerms, targetCollectionStats,
                docExpanders, expansionParams.getNumDocs());
        List<Double> interpolationWeights = new ArrayList<>(expansionParams.getExpWeights());
        interpolationWeights.add(0, expansionParams.getOrigWeight());
        rm1Builder.setInterpolationWeights(interpolationWeights);
		RM3Builder rm3Builder = new RM3Builder();

		// Prep the RM3 query
		GQuery rm3Query = new GQuery();
		rm3Query.setTitle(query.getTitle());

        // Prep the output
        FormattedOutputTrecEval out = FormattedOutputTrecEval.getInstance(
                "tmp",
                new BufferedWriter(new OutputStreamWriter(System.out))
        );

        for (int fbDocs = minFbDocs; fbDocs <= maxFbDocs; fbDocs += fbDocsInterval) {
            rm1Builder.setFeedbackDocs(fbDocs);

            FeatureVector rm1Vector = rm1Builder.buildRelevanceModel(query, feedbackDocs, stopper);

            for (int fbTerms = maxFbTerms; fbTerms >= minFbTerms; fbTerms -= fbTermsInterval) {
                rm1Vector.clip(fbTerms);

                for (int fbOrigWeightInt = 0; fbOrigWeightInt <= 10; fbOrigWeightInt++) {
                    double fbOrigWeight = fbOrigWeightInt / 10.0;

                    // Build the RM3
                    FeatureVector rm3 = rm3Builder.buildRelevanceModel(query, rm1Vector, fbOrigWeight);

                    // Set the RM3 as the query
                    rm3Query.setFeatureVector(rm3);

                    // Execute the RM3 query
                    SearchHits results = targetIndex.runQuery(rm3Query, 1000);

                    // Write results
                    String expWeightLabels = "";
                    for (int i = 1; i < interpolationWeights.size(); i++) {
                        expWeightLabels += "expW" + i + ":" + interpolationWeights.get(i);
                    }
                    out.setRunId(
                            expWeightLabels +
                                    ",v:" + expansionParams.getVecSize() +
                                    ",origW:" + expansionParams.getOrigWeight() +
                                    ",expDocs:" + expansionParams.getNumDocs() +
                                    ",expTerms:" + expansionParams.getNumTerms() +
                                    ",fbOrigWeight:" + fbOrigWeight +
                                    ",fbDocs:" + fbDocs +
                                    ",fbTerms:" + fbTerms
                    );
                    out.write(results, query.getTitle());
                }
            }
        }
	}
}
