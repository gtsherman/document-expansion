package org.retrievable.document_expansion.main;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.retrievable.document_expansion.expansion.DocumentExpander;
import org.retrievable.document_expansion.expansion.PreExpandedDocumentExpander;

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
import edu.gslis.scoring.queryscoring.QueryLikelihoodQueryScorer;
import edu.gslis.scoring.queryscoring.QueryScorer;
import edu.gslis.searchhits.IndexBackedSearchHit;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.textrepresentation.FeatureVector;
import edu.gslis.utils.Stopper;

public class RunDELM {

	public static void main(String[] args) throws ConfigurationException {
		// Load configuration
		Configuration config = new PropertiesConfiguration(args[0]);
		
		double origWeight = Double.parseDouble(args[1]);
		int numDocs = Integer.parseInt(args[2]);
		
		// Load resources
		Stopper stopper = new Stopper(config.getString("stoplist"));
		//IndexWrapper initialRetrievalIndex = new CachedFeatureVectorIndexWrapperIndriImpl(config.getString("target-index"), stopper);
		IndexWrapper targetIndex = new CachedFeatureVectorIndexWrapperIndriImpl(config.getString("target-index"));
		IndexWrapper expansionIndex = new CachedFeatureVectorIndexWrapperIndriImpl(config.getString("expansion-index"));
		GQueries queries = GQueriesFactory.getGQueries(config.getString("queries"));
		CollectionStats targetCollectionStats = new IndexBackedCollectionStats();
		targetCollectionStats.setStatSource(config.getString("target-index"));
		CollectionStats expansionCollectionStats = new IndexBackedCollectionStats();
		expansionCollectionStats.setStatSource(config.getString("expansion-index"));
		
		
		String clusters = args[3];
		Map<Integer, SearchHits> docToExpDocs = new HashMap<Integer, SearchHits>();
		try {
			Scanner scanner = new Scanner(new File(clusters));
			while (scanner.hasNextLine()) {
				String line = scanner.nextLine();
				String[] parts = line.split(" ");
				int docId = Integer.parseInt(parts[0]);
				
				SearchHits expansionDocs = docToExpDocs.getOrDefault(docId, new SearchHits());

				SearchHit expansionDoc = new IndexBackedSearchHit(expansionIndex);
				expansionDoc.setDocID(Integer.parseInt(parts[1]));
				expansionDoc.setScore(Double.parseDouble(parts[2]));
				expansionDocs.add(expansionDoc);

				docToExpDocs.put(docId, expansionDocs);
			}
			scanner.close();
		} catch (FileNotFoundException e) {
			System.err.println("Clusters not found");
			System.exit(-1);
		}
		
		// Setup scorers
		//DocumentExpander docExpander = new DocumentExpander(expansionIndex, numTerms, numDocs, stopper);
		DocumentExpander docExpander = new PreExpandedDocumentExpander(expansionIndex, 20, stopper, docToExpDocs);
		docExpander.setMaxNumDocs(numDocs);
		DocScorer scorer = new CachedDocScorer(new DirichletDocScorer(2500, expansionCollectionStats));
		QueryScorer queryScorer = new QueryLikelihoodQueryScorer(scorer);
		
		FormattedOutputTrecEval out = FormattedOutputTrecEval.getInstance("expanded", new BufferedWriter(new OutputStreamWriter(System.out)));

		Streams.stream(queries).forEach(query -> {
			query.applyStopper(stopper);
			SearchHits results = targetIndex.runQuery(query, 1000);
			
			for (int i = 0; i < results.size(); i++) {
				SearchHit doc = results.getHit(i);
				
				FeatureVector pseudoDoc = new FeatureVector(null);
				// Add orig counts * alpha
				for (String term : doc.getFeatureVector()) {
					pseudoDoc.addTerm(term, origWeight*doc.getFeatureVector().getFeatureWeight(term));
				}

				SearchHits expanded = docExpander.expandDocument(doc);
				for (SearchHit expDoc : expanded) {
					for (String term : expDoc.getFeatureVector()) {
						double neighborhoodWeight = expDoc.getScore() * expDoc.getFeatureVector().getFeatureWeight(term);
						pseudoDoc.addTerm(term, (1-origWeight)*neighborhoodWeight);
					}
				}
				
				doc.setFeatureVector(pseudoDoc);
				double expandedScore = queryScorer.scoreQuery(query, doc);
				doc.setScore(expandedScore);
			}
			
			results.rank();
			out.write(results, query.getTitle());
		});
	}

}
