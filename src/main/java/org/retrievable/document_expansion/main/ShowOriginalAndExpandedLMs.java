package org.retrievable.document_expansion.main;

import java.io.FileNotFoundException;
import java.util.Set;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.retrievable.document_expansion.DocumentExpander;
import org.retrievable.document_expansion.lms.LanguageModelEstimator;

import edu.gslis.docscoring.support.CollectionStats;
import edu.gslis.docscoring.support.IndexBackedCollectionStats;
import edu.gslis.eval.Qrels;
import edu.gslis.indexes.IndexWrapper;
import edu.gslis.indexes.IndexWrapperIndriImpl;
import edu.gslis.queries.GQueries;
import edu.gslis.queries.GQueriesFactory;
import edu.gslis.queries.GQuery;
import edu.gslis.scoring.expansion.RM1Builder;
import edu.gslis.scoring.expansion.StandardRM1Builder;
import edu.gslis.searchhits.IndexBackedSearchHit;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.textrepresentation.FeatureVector;
import edu.gslis.utils.Stopper;
import org.retrievable.document_expansion.scoring.ExpansionDocScorer;

public class ShowOriginalAndExpandedLMs {

	public static void main(String[] args) throws FileNotFoundException, ConfigurationException {
		Configuration config = new PropertiesConfiguration(args[0]);

		IndexWrapper targetIndex = new IndexWrapperIndriImpl(config.getString("target-index"));
		IndexWrapper expansionIndex = new IndexWrapperIndriImpl(config.getString("expansion-index"));
		CollectionStats cs = new IndexBackedCollectionStats();
		cs.setStatSource(config.getString("target-index"));
		GQueries queries = GQueriesFactory.getGQueries(config.getString("queries"));
		Stopper stopper = new Stopper(config.getString("stoplist"));
		
		Qrels qrels = new Qrels(config.getString("qrels"), false, 1);
		
		DocumentExpander docExpander = new DocumentExpander(expansionIndex, stopper);
		
		//for (GQuery query : queries) {
		GQuery query = queries.getIthQuery(0);
			System.out.println("*** QUERY: " + query.getText() + " ***");

			//SearchHits docs = targetIndex.runQuery(query, 10);
			Set<String> relDocs = qrels.getRelDocs(query.getTitle());
			SearchHits docs = new SearchHits();
			/*for (String relDoc : relDocs) {
				SearchHit doc = new IndexBackedSearchHit(targetIndex);
				doc.setDocno(relDoc);
				docs.add(doc);
			}*/
			SearchHit d = new IndexBackedSearchHit(targetIndex);
			d.setDocno(args[1]);
			docs.add(d);

			RM1Builder rm1Builder = new StandardRM1Builder(docs.size(), 20, cs);
			for (SearchHit doc : docs) {		
				FeatureVector origLM = LanguageModelEstimator.languageModel(doc, targetIndex);
				origLM.applyStopper(stopper);
				FeatureVector expLM = LanguageModelEstimator.expansionLanguageModel(doc, new ExpansionDocScorer(docExpander));
				expLM.applyStopper(stopper);
			//	FeatureVector rm1 = rm1Builder.buildRelevanceModel(query, docs, stopper);
				
				int terms = 10;
				origLM.clip(terms);
				expLM.clip(terms);
				
				System.out.println("Original:");
				System.out.println(origLM.toString());
				System.out.println("Expanded:");
				System.out.println(expLM.toString());
			//	System.out.println("RM1:");
			//	System.out.println(rm1.toString(10));
				System.out.println();
				System.out.println();
			}
		//}
	}

}
