package org.retrievable.document_expansion.main;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.retrievable.document_expansion.DocumentExpander;
import org.retrievable.document_expansion.scoring.ExpansionDocScorer;

import com.google.common.collect.Streams;

import edu.gslis.docscoring.support.CollectionStats;
import edu.gslis.docscoring.support.IndexBackedCollectionStats;
import edu.gslis.indexes.CachedFeatureVectorIndexWrapperIndriImpl;
import edu.gslis.indexes.IndexWrapper;
import edu.gslis.output.FormattedOutputTrecEval;
import edu.gslis.queries.GQueries;
import edu.gslis.queries.GQueriesFactory;
import edu.gslis.scoring.CachedDocScorer;
import edu.gslis.scoring.DirichletDocScorer;
import edu.gslis.scoring.DocScorer;
import edu.gslis.scoring.InterpolatedDocScorer;
import edu.gslis.scoring.queryscoring.QueryLikelihoodQueryScorer;
import edu.gslis.scoring.queryscoring.QueryScorer;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.utils.Stopper;

public class RunExpandedRetrieval {

	public static void main(String[] args) throws ConfigurationException {
		// Load configuration
		Configuration config = new PropertiesConfiguration(args[0]);
		
		double origWeight = Double.parseDouble(args[1]);
		int numDocs = Integer.parseInt(args[2]);
		int numTerms = Integer.parseInt(args[3]);
		
		// Load resources
		Stopper stopper = new Stopper(config.getString("stoplist"));
		//IndexWrapper initialRetrievalIndex = new CachedFeatureVectorIndexWrapperIndriImpl(config.getString("target-index"), stopper);
		IndexWrapper targetIndex = new CachedFeatureVectorIndexWrapperIndriImpl(config.getString("target-index"));
		IndexWrapper expansionIndex = new CachedFeatureVectorIndexWrapperIndriImpl(config.getString("expansion-index"));
		GQueries queries = GQueriesFactory.getGQueries(config.getString("queries"));
		CollectionStats targetCollectionStats = new IndexBackedCollectionStats();
		targetCollectionStats.setStatSource(config.getString("target-index"));
		
		/*
		String clusters = args[4];
		Map<String, SearchHits> docToExpDocs = new HashMap<String, SearchHits>();
		try {
			Scanner scanner = new Scanner(new File(clusters));
			while (scanner.hasNextLine()) {
				String line = scanner.nextLine();
				String[] parts = line.split("\t");
				
				SearchHits expansionDocs = docToExpDocs.getOrDefault(parts[0], new SearchHits());

				SearchHit expansionDoc = new IndexBackedSearchHit(expansionIndex);
				expansionDoc.setDocno(parts[1]);
				expansionDoc.setScore(Double.parseDouble(parts[2]));
				expansionDocs.add(expansionDoc);

				docToExpDocs.put(parts[0], expansionDocs);
			}
			scanner.close();
		} catch (FileNotFoundException e) {
			System.err.println("Clusters not found");
			System.exit(-1);
		}*/
		
		// Setup scorers
		DocumentExpander docExpander = new DocumentExpander(expansionIndex, numTerms, numDocs, stopper);
//		DocumentExpander docExpander = new PreExpandedDocumentExpander(expansionIndex, docToExpDocs, numDocs);
		Map<DocScorer, Double> scorers = new HashMap<DocScorer, Double>();
		scorers.put(new CachedDocScorer(new DirichletDocScorer(targetCollectionStats)), origWeight);
		scorers.put(new CachedDocScorer(new ExpansionDocScorer(2500, docExpander)), 1-origWeight);
		DocScorer interpolatedScorer = new InterpolatedDocScorer(scorers);
		QueryScorer queryScorer = new QueryLikelihoodQueryScorer(interpolatedScorer);
		
		FormattedOutputTrecEval out = FormattedOutputTrecEval.getInstance("expanded", new BufferedWriter(new OutputStreamWriter(System.out)));

		Streams.stream(queries).forEach(query -> {
			query.applyStopper(stopper);
			SearchHits results = targetIndex.runQuery(query, 1000);
			
			/** TODO: See if it's more effective to expand all results. Realizing it's much slower, but hopefully faster when we parallelize out per query. **/
			for (int i = 0; i < results.size(); i++) {
				SearchHit doc = results.getHit(i);
				double expandedScore = queryScorer.scoreQuery(query, doc);
				doc.setScore(expandedScore);
			}
			
			results.rank();
			out.write(results, query.getTitle());
		});
	}

}
