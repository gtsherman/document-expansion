package org.retrievable.document_expansion.main;

import edu.gslis.docscoring.support.CollectionStats;
import edu.gslis.docscoring.support.IndexBackedCollectionStats;
import edu.gslis.indexes.CachedFeatureVectorIndexWrapperIndriImpl;
import edu.gslis.indexes.IndexWrapper;
import edu.gslis.output.FormattedOutputTrecEval;
import edu.gslis.queries.GQueries;
import edu.gslis.queries.GQueriesFactory;
import edu.gslis.queries.GQuery;
import edu.gslis.scoring.expansion.RM1Builder;
import edu.gslis.scoring.expansion.RM3Builder;
import edu.gslis.scoring.expansion.StandardRM1Builder;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.textrepresentation.FeatureVector;
import edu.gslis.utils.Stopper;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.retrievable.document_expansion.DocumentExpander;
import org.retrievable.document_expansion.lms.LanguageModelEstimator;
import org.retrievable.document_expansion.scoring.ExpansionDocScorer;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;

public class RunExpandedRMRetrieval {

	public static void main(String[] args) throws ConfigurationException {
		// Load configuration
		Configuration config = new PropertiesConfiguration(args[0]);

		int numTerms = Integer.parseInt(args[1]);
		String queryName = args[2];

		// Load resources
		Stopper stopper = new Stopper(config.getString("stoplist"));

		IndexWrapper targetIndex = new CachedFeatureVectorIndexWrapperIndriImpl(config.getString("target-index"));
		IndexWrapper expansionIndex = new CachedFeatureVectorIndexWrapperIndriImpl(config.getString("expansion-index"));

		GQueries queries = GQueriesFactory.getGQueries(config.getString("queries"));
		GQuery query = queries.getNamedQuery(queryName);

		CollectionStats targetCollectionStats = new IndexBackedCollectionStats();
		targetCollectionStats.setStatSource(config.getString("target-index"));

		int minNumDocs = Integer.parseInt(config.getString("min-docs", "5"));
		int maxNumDocs = Integer.parseInt(config.getString("max-docs", "25"));
		int numDocsInterval = Integer.parseInt(config.getString("docs-interval", "5"));

		int minFbDocs = Integer.parseInt(config.getString("min-fbdocs", "10"));
		int maxFbDocs = Integer.parseInt(config.getString("max-fbdocs", "50"));
		int fbDocsInterval = Integer.parseInt(config.getString("fbdocs-interval", "20"));

		int minFbTerms = Integer.parseInt(config.getString("min-fbterms", "5"));
		int maxFbTerms = Integer.parseInt(config.getString("max-fbterms", "25"));
		int fbTermsInterval = Integer.parseInt(config.getString("fbterms-interval", "10"));

		DocumentExpander docExpander = new DocumentExpander(expansionIndex, numTerms, stopper);
		docExpander.setMaxNumDocs(maxNumDocs);

		FormattedOutputTrecEval out = FormattedOutputTrecEval.getInstance(
				"tmp",
				new BufferedWriter(new OutputStreamWriter(System.out))
		);

		// Get the feedback docs
		query.applyStopper(stopper);
		SearchHits feedbackDocs = targetIndex.runQuery(query, maxFbDocs); // get max fbDocs; we'll trim as we go along

		// Prep RM3 builder
		RM3Builder rm3Builder = new RM3Builder();

		// Prep the RM3 query
		GQuery rm3Query = new GQuery();
		rm3Query.setTitle(query.getTitle());

		for (int numDocs = minNumDocs; numDocs <= maxNumDocs; numDocs += numDocsInterval) {

			for (SearchHit doc : feedbackDocs) {
				// Get language models
				ExpansionDocScorer expansionDocScorer = new ExpansionDocScorer(docExpander, numDocs);
				FeatureVector originalLM = LanguageModelEstimator.languageModel(doc, targetCollectionStats);
				FeatureVector expansionLM = LanguageModelEstimator.expansionLanguageModel(doc, expansionDocScorer);

				doc.setMetadataValue("originalLM", originalLM);
				doc.setMetadataValue("expansionLM", expansionLM);
			}

			// Build RM1 with the maximum fbTerms so that we don't have to rebuild it for each smaller
			// number of fbTerms but can just clip it down
			StandardRM1Builder rm1Builder = new StandardRM1Builder(maxFbDocs, maxFbTerms, targetCollectionStats);

            for (int fbDocs = maxFbDocs; fbDocs >= minFbDocs; fbDocs -= fbDocsInterval) {

            	rm1Builder.setFeedbackDocs(fbDocs);

				for (int origWeightInt = 0; origWeightInt <= 10; origWeightInt++) {
					double origWeight = origWeightInt / 10.0;

					// For now, we only need fbDocs number of documents because we are just building our RM
					for (SearchHit doc : feedbackDocs) {
						FeatureVector originalLM = (FeatureVector) doc.getMetadataValue("originalLM");
						FeatureVector expansionLM = (FeatureVector) doc.getMetadataValue("expansionLM");

						FeatureVector expandedLM = LanguageModelEstimator.combinedLanguageModel(originalLM, expansionLM, origWeight);

						// Set the feedback document's feature vector to be the expansion language model
						doc.setFeatureVector(expandedLM);
					}

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
				}
			}
		}
	}
}
