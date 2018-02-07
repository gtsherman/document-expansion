package org.retrievable.document_expansion.main;

import edu.gslis.docscoring.support.CollectionStats;
import edu.gslis.docscoring.support.IndexBackedCollectionStats;
import edu.gslis.indexes.CachedFeatureVectorIndexWrapperIndriImpl;
import edu.gslis.indexes.IndexWrapper;
import edu.gslis.output.FormattedOutputTrecEval;
import edu.gslis.queries.GQueries;
import edu.gslis.queries.GQueriesFactory;
import edu.gslis.queries.GQuery;
import edu.gslis.scoring.expansion.RM3Builder;
import edu.gslis.scoring.expansion.StandardRM1Builder;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.textrepresentation.FeatureVector;
import edu.gslis.utils.Stopper;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;

public class RunBaselineRM3 {

    public static void main(String[] args) throws ConfigurationException {
        // Load configuration
		Configuration config = new PropertiesConfiguration(args[0]);

		double fbOrigWeight = Double.parseDouble(args[1]);

		// Load resources
		Stopper stopper = new Stopper(config.getString("stoplist"));

		IndexWrapper targetIndex = new CachedFeatureVectorIndexWrapperIndriImpl(config.getString("target-index"));

		GQueries queries = GQueriesFactory.getGQueries(config.getString("queries"));

		CollectionStats targetCollectionStats = new IndexBackedCollectionStats();
		targetCollectionStats.setStatSource(config.getString("target-index"));

		FormattedOutputTrecEval out = FormattedOutputTrecEval.getInstance(
				"rm3",
				new BufferedWriter(new OutputStreamWriter(System.out))
		);

		StandardRM1Builder rm1Builder = new StandardRM1Builder(20, 20, targetCollectionStats);
		RM3Builder rm3Builder = new RM3Builder();

		for (GQuery query : queries) {
			query.applyStopper(stopper);

			SearchHits feedbackDocs = targetIndex.runQuery(query, 20);
			FeatureVector rm3 = rm3Builder.buildRelevanceModel(query, feedbackDocs, rm1Builder, fbOrigWeight, stopper);
			query.setFeatureVector(rm3);

			out.write(targetIndex.runQuery(query, 1000), query.getTitle());
		}

		out.close();
    }

}
