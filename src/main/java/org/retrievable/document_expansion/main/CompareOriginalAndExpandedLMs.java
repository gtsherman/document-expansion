package org.retrievable.document_expansion.main;

import java.io.FileNotFoundException;
import java.util.Set;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.retrievable.document_expansion.expansion.DocumentExpander;
import org.retrievable.document_expansion.lms.LanguageModelEstimator;
import org.retrievable.document_expansion.features.LMFeatures;

import com.google.common.collect.Streams;

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

public class CompareOriginalAndExpandedLMs {

	public static void main(String[] args) throws FileNotFoundException, ConfigurationException {
		Configuration config = new PropertiesConfiguration(args[0]);
		IndexWrapper targetIndex = new IndexWrapperIndriImpl(config.getString("target-index"));
		CollectionStats cs = new IndexBackedCollectionStats();
		cs.setStatSource(config.getString("target-index"));
		IndexWrapper expansionIndex = new IndexWrapperIndriImpl(config.getString("expansion-index"));
		Stopper stopper = new Stopper(config.getString("stoplist"));
		GQueries queries = GQueriesFactory.getGQueries(config.getString("queries"));
		
		DocumentExpander docExpander = new DocumentExpander(expansionIndex, stopper);
		
		Qrels qrels = new Qrels(config.getString("qrels"), false, 1);
		for (GQuery query : queries) {
			//SearchHits docs = targetIndex.runQuery(query, 10);
			Set<String> relDocs = qrels.getRelDocs(query.getTitle());
			SearchHits docs = new SearchHits();
			for (String relDoc : relDocs) {
				SearchHit doc = new IndexBackedSearchHit(targetIndex);
				doc.setDocno(relDoc);
				docs.add(doc);
			}

			RM1Builder rm1Builder = new StandardRM1Builder(docs.size(), Integer.MAX_VALUE, cs);
			
			Streams.stream(docs).forEach(doc -> {
				System.err.println("Working on doc " + doc.getDocno());

				FeatureVector rm1 = rm1Builder.buildRelevanceModel(query, docs, stopper);

				FeatureVector origLM = LanguageModelEstimator.languageModel(doc, targetIndex);
				origLM.applyStopper(stopper);
				FeatureVector origLM2 = new FeatureVector(null);
				origLM.forEach(term -> {
					if (rm1.contains(term)) {
						origLM2.addTerm(term, origLM.getFeatureWeight(term));
					}
				});

				FeatureVector expLM = LanguageModelEstimator.expansionLanguageModel(doc, new ExpansionDocScorer(docExpander));
				FeatureVector expLM2 = new FeatureVector(null);
				origLM.forEach(term -> {
					if (rm1.contains(term)) {
						expLM2.addTerm(term, expLM.getFeatureWeight(term));
					}
				});
				expLM.applyStopper(stopper);
		
				double origRMkl = LMFeatures.languageModelsKL(origLM2, rm1);
				double expRMkl = LMFeatures.languageModelsKL(expLM2, rm1);
				double origExpKL = LMFeatures.languageModelsKL(origLM2, expLM2);
				System.out.println(query.getTitle() + "," + 
						doc.getDocno() + "," +
						origRMkl + "," +
						expRMkl + "," +
						origExpKL);
			});
		}
	}

}
