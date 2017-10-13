package org.retrievable.document_expansion.main;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;

import com.google.common.collect.Streams;

import edu.gslis.docscoring.support.CollectionStats;
import edu.gslis.docscoring.support.IndexBackedCollectionStats;
import edu.gslis.indexes.CachedFeatureVectorIndexWrapperIndriImpl;
import edu.gslis.indexes.IndexWrapper;
import edu.gslis.output.FormattedOutputTrecEval;
import edu.gslis.queries.GQueries;
import edu.gslis.queries.GQueriesFactory;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.utils.Stopper;

public class RunBaseline {

	public static void main(String[] args) throws ConfigurationException {
		// Load configuration
		Configuration config = new PropertiesConfiguration(args[0]);
		
		// Load resources
		Stopper stopper = new Stopper(config.getString("stoplist"));
		IndexWrapper targetIndex = new CachedFeatureVectorIndexWrapperIndriImpl(config.getString("target-index"), stopper);
		GQueries queries = GQueriesFactory.getGQueries(config.getString("queries"));
		CollectionStats targetCollectionStats = new IndexBackedCollectionStats();
		targetCollectionStats.setStatSource(config.getString("target-index"));
		
		// Setup scorers
		//DocScorer scorer = new DirichletDocScorer(targetCollectionStats);
		//QueryScorer queryScorer = new QueryLikelihoodQueryScorer(scorer);
		
		FormattedOutputTrecEval out = FormattedOutputTrecEval.getInstance("expanded", new BufferedWriter(new OutputStreamWriter(System.out)));

		Streams.stream(queries).forEach(query -> {
//			query.applyStopper(stopper);
			
			SearchHits results = targetIndex.runQuery(query, 1000);
			/** TODO: See if it's more effective to expand all results. Realizing it's much slower, but hopefully faster when we parallelize out per query. **/
			/*for (int i = 0; i < Math.min(results.size(), 100); i++) {
				SearchHit doc = results.getHit(i);
				double expandedScore = queryScorer.scoreQuery(query, doc);
				doc.setScore(expandedScore);
			}
			
			results.rank();*/
			out.write(results, query.getTitle());
		});
	}

}
