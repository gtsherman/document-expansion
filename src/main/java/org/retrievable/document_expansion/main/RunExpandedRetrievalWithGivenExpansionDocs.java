package org.retrievable.document_expansion.main;

import edu.gslis.docscoring.support.CollectionStats;
import edu.gslis.docscoring.support.IndexBackedCollectionStats;
import edu.gslis.indexes.CachedFeatureVectorIndexWrapperIndriImpl;
import edu.gslis.indexes.IndexWrapperIndriImpl;
import edu.gslis.output.FormattedOutputTrecEval;
import edu.gslis.queries.GQueries;
import edu.gslis.queries.GQueriesFactory;
import edu.gslis.queries.GQuery;
import edu.gslis.scoring.CachedDocScorer;
import edu.gslis.scoring.DirichletDocScorer;
import edu.gslis.scoring.DocScorer;
import edu.gslis.scoring.InterpolatedDocScorer;
import edu.gslis.scoring.queryscoring.QueryLikelihoodQueryScorer;
import edu.gslis.scoring.queryscoring.QueryScorer;
import edu.gslis.searchhits.IndexBackedSearchHit;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.utils.Stopper;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.retrievable.document_expansion.expansion.DocumentExpander;
import org.retrievable.document_expansion.expansion.PreExpandedDocumentExpander;
import org.retrievable.document_expansion.lms.InterpolationWeights;
import org.retrievable.document_expansion.scoring.ExpansionDocScorer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.OutputStreamWriter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * New model:
 * Run this code
 */
public class RunExpandedRetrievalWithGivenExpansionDocs {

    public static void main(String[] args) throws ConfigurationException {
        // Load configuration
        Configuration config = new PropertiesConfiguration(args[0]);

        // Load run parameters
        int numTerms = Integer.parseInt(args[1]);
        String queryName = args[2];
        String expansionDocsFile = args[3];

        // Load resources
        Stopper stopper = new Stopper(config.getString("stoplist"));

        IndexWrapperIndriImpl targetIndex = new IndexWrapperIndriImpl(config.getString("target-index"));
        List<IndexWrapperIndriImpl> expansionIndexes = Arrays.stream(config.getStringArray("expansion-index"))
                .map(CachedFeatureVectorIndexWrapperIndriImpl::new)
                .collect(Collectors.toList());

        GQueries queries = GQueriesFactory.getGQueries(config.getString("queries"));
        GQuery query = queries.getNamedQuery(queryName);

        CollectionStats targetCollectionStats = new IndexBackedCollectionStats();
        targetCollectionStats.setStatSource(config.getString("target-index"));

        int minNumDocs = Integer.parseInt(config.getString("min-docs", "5"));
        int maxNumDocs = Integer.parseInt(config.getString("max-docs", "25"));
        int numDocsInterval = Integer.parseInt(config.getString("docs-interval", "5"));

        Map<Integer, SearchHits> expansionDocs = new HashMap<>();
        try {
            Scanner scanner = new Scanner(new File(expansionDocsFile));
            while (scanner.hasNextLine()) {
                String[] parts = scanner.nextLine().split(",");
                String origDocno = parts[0];
                int origDocID = targetIndex.getDocId(origDocno);
                String relatedDocno = parts[1];
                double cosine = Double.parseDouble(parts[2]);

                IndexBackedSearchHit expHit = new IndexBackedSearchHit(targetIndex);
                expHit.setDocno(relatedDocno);
                expHit.setScore(cosine);

                if (!expansionDocs.containsKey(origDocID)) {
                    expansionDocs.put(origDocID, new SearchHits());
                }
                expansionDocs.get(origDocID).add(expHit);
            }
        } catch (FileNotFoundException e) {
            System.out.println("Couldn't open file: " + expansionDocsFile);
            System.exit(-1);
        }

        List<DocumentExpander> docExpanders = expansionIndexes
                .stream()
                .map(expansionIndex -> new PreExpandedDocumentExpander(expansionIndex, numTerms, stopper, expansionDocs))
                .collect(Collectors.toList());
        docExpanders.stream().forEach(docExpander -> docExpander.setMaxNumDocs(maxNumDocs));

        FormattedOutputTrecEval out = FormattedOutputTrecEval.getInstance(
                "tmp",
                new BufferedWriter(new OutputStreamWriter(System.out))
        );

        // Create scorers
        DocScorer dirichletScorer = new CachedDocScorer(new DirichletDocScorer(targetCollectionStats));
        List<ExpansionDocScorer> expansionScorers = docExpanders
                .stream()
                .map(docExpander -> new ExpansionDocScorer(2500, docExpander))
                .collect(Collectors.toList());

        Map<DocScorer, Double> scorers = new HashMap<>();
        DocScorer interpolatedScorer = new InterpolatedDocScorer(scorers);
        QueryScorer queryScorer = new QueryLikelihoodQueryScorer(interpolatedScorer);

        // Get initial results
        query.applyStopper(stopper);
        SearchHits results = targetIndex.runQuery(query, 1000);

        // Iterate over parameter settings
        for (int numDocs = minNumDocs; numDocs <= maxNumDocs; numDocs += numDocsInterval) {
            for (ExpansionDocScorer expansionScorer : expansionScorers) {
                expansionScorer.setNumDocs(numDocs);
            }

            // Add 1 for the original document scorer
            List<List<Double>> interpolationWeights = InterpolationWeights.weights(expansionScorers.size() + 1);
            for (List<Double> interpolationWeightCombination : interpolationWeights) {
                double origWeight = interpolationWeightCombination.get(0);
                scorers.put(dirichletScorer, origWeight);

                String expansionWeights = "";
                for (int i = 0; i < expansionScorers.size(); i++) {
                    double expansionWeight = interpolationWeightCombination.get(i + 1);

                    ExpansionDocScorer expansionScorer = expansionScorers.get(i);
                    scorers.put(expansionScorer, expansionWeight);

                    expansionWeights += "expW" + (i + 1) + ":" + expansionWeight;
                }

                out.setRunId(expansionWeights + ",origW:" + origWeight + ",expDocs:" + numDocs + ",expTerms:" + numTerms);

                for (SearchHit doc : results) {
                    double expandedScore = queryScorer.scoreQuery(query, doc);
                    doc.setScore(expandedScore);
                }

                results.rank();
                out.write(results, query.getTitle());
            }
        }

        out.close();
    }

}
