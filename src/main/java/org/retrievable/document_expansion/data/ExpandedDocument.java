package org.retrievable.document_expansion.data;

import java.util.Set;

import org.retrievable.document_expansion.DocumentExpander;
import org.retrievable.document_expansion.features.FeatureUtils;
import org.retrievable.document_expansion.scoring.ExpansionDocScorer;

import com.google.common.collect.Streams;

import edu.gslis.docscoring.support.IndexBackedCollectionStats;
import edu.gslis.indexes.IndexWrapper;
import edu.gslis.scoring.DirichletDocScorer;
import edu.gslis.scoring.DocScorer;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.textrepresentation.FeatureVector;

public class ExpandedDocument {
	
	private SearchHit originalDocument;
	private SearchHits expansionDocuments;
	
	private FeatureVector originalLM;
	private FeatureVector expansionLM;
	
	public ExpandedDocument(SearchHit originalDocument, SearchHits expansionDocuments) {
		this.originalDocument = originalDocument;
		this.expansionDocuments = expansionDocuments;
	}
	
	public SearchHit getOriginalDocument() {
		return originalDocument;
	}
	
	public SearchHits getExpansionDocuments() {
		return expansionDocuments;
	}
	
	public FeatureVector expansionLanguageModel(IndexWrapper expansionIndex) {
		if (expansionLM == null) {
			// Add all of the FeatureVectors into an array so we can create a vocabulary
			FeatureVector[] vectors = new FeatureVector[expansionDocuments.size()];
			for (int i = 0; i < vectors.length; i++) {
				vectors[i] = expansionDocuments.getHit(i).getFeatureVector();
			}
			Set<String> vocabulary = FeatureUtils.createVocabulary(vectors);
			
			// Create a scorer that uses the expansion documents' existing scores as P(E|D)
			// This seems like a bad way to do things...
			DocumentExpander docExpander = new DocumentExpander(expansionIndex);
			docExpander.addExpansionDocument(this);
			DocScorer scorer = new ExpansionDocScorer(docExpander);
			
			// Create the language model
			expansionLM = new FeatureVector(null);
			vocabulary.stream().forEach(term -> {
				expansionLM.addTerm(term, scorer.scoreTerm(term, originalDocument));
			});
		
			// Clear the individual feature vectors because they are serious memory hogs
			Streams.stream(expansionDocuments).forEach(doc -> doc.setFeatureVector(null));
		}

		return expansionLM;
	}
	
	public FeatureVector originalLanguageModel(IndexWrapper targetIndex) {
		if (originalLM == null) {
			IndexBackedCollectionStats collectionStats = new IndexBackedCollectionStats();
			collectionStats.setStatSource(targetIndex);
			
			originalLM = new FeatureVector(null);
			DocScorer scorer = new DirichletDocScorer(collectionStats);
			for (String term : originalDocument.getFeatureVector().getFeatures()) {
				originalLM.addTerm(term, scorer.scoreTerm(term, originalDocument));
			}
		}
		return originalLM;
	}
	
	public FeatureVector languageModel(IndexWrapper index, double originalLMWeight) {
		return languageModel(index, index, originalLMWeight);
	}
	
	public FeatureVector languageModel(IndexWrapper targetIndex, IndexWrapper expansionIndex, double originalLMWeight) {
		FeatureVector origLM = originalLanguageModel(targetIndex);
		FeatureVector expansionLM = expansionLanguageModel(expansionIndex);
		
		Set<String> vocabulary = FeatureUtils.createVocabulary(origLM, expansionLM);

		FeatureVector lm = new FeatureVector(null);
		vocabulary.stream().parallel().forEach(term -> {
			lm.addTerm(term, originalLMWeight * origLM.getFeatureWeight(term) + (1 - originalLMWeight) * expansionLM.getFeatureWeight(term));
		});
		return lm;
	}
	
}
