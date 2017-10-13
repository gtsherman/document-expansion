package org.retrievable.document_expansion.main;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.retrievable.document_expansion.DocumentExpander;
import org.retrievable.document_expansion.data.ExpandedDocument;

import com.google.common.collect.Streams;

import edu.gslis.docscoring.support.CollectionStats;
import edu.gslis.docscoring.support.IndexBackedCollectionStats;
import edu.gslis.indexes.CachedFeatureVectorIndexWrapperIndriImpl;
import edu.gslis.indexes.IndexWrapper;
import edu.gslis.output.FormattedOutputTrecEval;
import edu.gslis.queries.GQueries;
import edu.gslis.queries.GQueriesFactory;
import edu.gslis.scoring.expansion.RM1Builder;
import edu.gslis.scoring.expansion.RM3Builder;
import edu.gslis.scoring.expansion.StandardRM1Builder;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.textrepresentation.FeatureVector;
import edu.gslis.utils.Stopper;

public class RunExpandedRMRetrieval {

	public static void main(String[] args) throws ConfigurationException {
		// Load configuration
		Configuration config = new PropertiesConfiguration(args[0]);
		
		double origWeight = Double.parseDouble(args[1]);
		int numDocs = Integer.parseInt(args[2]);
		int numTerms = Integer.parseInt(args[3]);

		double fbOrigWeight = Double.parseDouble(args[4]);
		int fbDocs = Integer.parseInt(args[5]);
		int fbTerms = Integer.parseInt(args[6]);
		
		// Load resources
		IndexWrapper targetIndex = new CachedFeatureVectorIndexWrapperIndriImpl(config.getString("target-index"));
		IndexWrapper expansionIndex = new CachedFeatureVectorIndexWrapperIndriImpl(config.getString("expansion-index"));
		Stopper stopper = new Stopper(config.getString("stoplist"));
		GQueries queries = GQueriesFactory.getGQueries(config.getString("queries"));
		CollectionStats targetCollectionStats = new IndexBackedCollectionStats();
		targetCollectionStats.setStatSource(config.getString("target-index"));
		
		DocumentExpander docExpander = new DocumentExpander(expansionIndex, numTerms, numDocs, stopper);
		
		FormattedOutputTrecEval out = FormattedOutputTrecEval.getInstance("expanded", new BufferedWriter(new OutputStreamWriter(System.out)));

		Streams.stream(queries).forEach(query -> {
			query.applyStopper(stopper);
			
			// For now, we only need fbDocs number of documents because we are just building our RM 
			SearchHits feedbackDocs = targetIndex.runQuery(query, fbDocs);
			for (SearchHit doc : feedbackDocs) {
				// Set the feedback document's feature vector to be the expansion language model
				ExpandedDocument expandedDoc = docExpander.expandDocument(doc);
				doc.setFeatureVector(expandedDoc.languageModel(targetIndex, expansionIndex, origWeight));
			}

			RM1Builder rm1Builder = new StandardRM1Builder(fbDocs, fbTerms, targetCollectionStats);
			RM3Builder rm3Builder = new RM3Builder();
			FeatureVector rm3 = rm3Builder.buildRelevanceModel(query, feedbackDocs, rm1Builder, fbOrigWeight, stopper);
			
			query.setFeatureVector(rm3);
			SearchHits results = targetIndex.runQuery(query, 1000);
			out.write(results, query.getTitle());
		});
	}

}
