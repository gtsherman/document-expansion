package org.retrievable.document_expansion.main;

import edu.gslis.docscoring.support.CollectionStats;
import edu.gslis.docscoring.support.IndexBackedCollectionStats;
import edu.gslis.indexes.CachedFeatureVectorIndexWrapperIndriImpl;
import edu.gslis.indexes.IndexWrapper;
import edu.gslis.output.FormattedOutputTrecEval;
import edu.gslis.queries.GQueries;
import edu.gslis.queries.GQueriesFactory;
import edu.gslis.queries.GQuery;
import edu.gslis.scoring.expansion.ExpandedRM1Builder;
import edu.gslis.scoring.expansion.RM3Builder;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.textrepresentation.FeatureVector;
import edu.gslis.utils.Stopper;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.retrievable.document_expansion.expansion.DocumentExpander;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.OutputStreamWriter;
import java.util.Scanner;

public class RunExpandedRMRetrieval {

	public static void main(String[] args) throws ConfigurationException {
		// Load configuration
		Configuration config = new PropertiesConfiguration(args[0]);

		//int numTerms = Integer.parseInt(args[1]);
		String queryName = args[1];

		// Load resources
		Stopper stopper = new Stopper(config.getString("stoplist"));

		IndexWrapper targetIndex = new CachedFeatureVectorIndexWrapperIndriImpl(config.getString("target-index"));
		IndexWrapper expansionIndex = new CachedFeatureVectorIndexWrapperIndriImpl(config.getString("expansion-index"));

		GQueries queries = GQueriesFactory.getGQueries(config.getString("queries"));
		GQuery query = queries.getNamedQuery(queryName);

		CollectionStats targetCollectionStats = new IndexBackedCollectionStats();
		targetCollectionStats.setStatSource(config.getString("target-index"));

		// Ugliness that basically means read in a file that shows the optimal params determined for a non-RM3 run
		// (i.e. optimal exp. docs and terms) and set numDocs and numTerms to those values respectively. We will take
		// those as optimal for expansion purposes and sweep only over RM3-specific parameters.
		int numDocs = 5;
		int numTerms = 5;
		double origWeight = 1.0;
		String paramsFile = config.getString("optimal-params");
		try {
			Scanner scanner = new Scanner(new File(paramsFile));
			while (scanner.hasNextLine()) {
				String line = scanner.nextLine();
				String[] parts = line.split(" ");
				if (parts[0].equals(queryName)) {
					String[] paramKeyVals = parts[1].trim().split(",");
					origWeight = Double.parseDouble(paramKeyVals[0].split(":")[1]);
					numDocs = Integer.parseInt(paramKeyVals[1].split(":")[1]);
					numTerms = Integer.parseInt(paramKeyVals[2].split(":")[1]);
					break;
				}
			}
			scanner.close();
		} catch (FileNotFoundException e) {
			System.err.println(paramsFile + " not found");
			System.exit(-1);
		}

		int minFbDocs = Integer.parseInt(config.getString("min-fbdocs", "10"));
		int maxFbDocs = Integer.parseInt(config.getString("max-fbdocs", "50"));
		int fbDocsInterval = Integer.parseInt(config.getString("fbdocs-interval", "10"));

		int minFbTerms = Integer.parseInt(config.getString("min-fbterms", "10"));
		int maxFbTerms = Integer.parseInt(config.getString("max-fbterms", "50"));
		int fbTermsInterval = Integer.parseInt(config.getString("fbterms-interval", "10"));

		DocumentExpander docExpander = new DocumentExpander(expansionIndex, numTerms, stopper);
		docExpander.setMaxNumDocs(numDocs);

		// Get the feedback docs
		query.applyStopper(stopper);
		SearchHits feedbackDocs = targetIndex.runQuery(query, maxFbDocs); // get max fbDocs; we'll trim as we go along

		// Prep RM builders
		ExpandedRM1Builder rm1Builder = new ExpandedRM1Builder(maxFbDocs, maxFbTerms, targetCollectionStats, docExpander, numDocs);
		rm1Builder.setOriginalLMWeight(origWeight);
		RM3Builder rm3Builder = new RM3Builder();

		// Prep the RM3 query
		GQuery rm3Query = new GQuery();
		rm3Query.setTitle(query.getTitle());

        // Prep the output
        FormattedOutputTrecEval out = FormattedOutputTrecEval.getInstance(
                "tmp",
                new BufferedWriter(new OutputStreamWriter(System.out))
        );

        for (int fbDocs = maxFbDocs; fbDocs >= minFbDocs; fbDocs -= fbDocsInterval) {
            rm1Builder.setFeedbackDocs(fbDocs);

            //for (int origWeightInt = 0; origWeightInt <= 10; origWeightInt++) {
            //    double origWeight = origWeightInt / 10.0;

                FeatureVector rm1Vector = rm1Builder.buildRelevanceModel(query, feedbackDocs, stopper);

                for (int fbTerms = maxFbTerms; fbTerms >= minFbTerms; fbTerms -= fbTermsInterval) {
                    // As with fbDocs, work backwards through fbTerms
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
                        out.setRunId("origW:" + origWeight +
                                        ",expDocs:" + numDocs +
                                        ",expTerms:" + numTerms +
                                        ",fbOrigWeight:" + fbOrigWeight +
                                        ",fbDocs:" + fbDocs +
                                        ",fbTerms:" + fbTerms
                        );
                        out.write(results, query.getTitle());
                    }
                }
            //}
        }
	}
}
