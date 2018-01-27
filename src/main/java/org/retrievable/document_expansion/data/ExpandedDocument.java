package org.retrievable.document_expansion.data;

import java.util.List;

import edu.gslis.searchhits.SearchHitUtilsKt;
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
	
	public SearchHits getExpansionDocuments(int limit) {
	    SearchHits croppedDocs = new SearchHits();
	    for (int i = 0; i < Math.min(limit, expansionDocuments.size()); i++) {
	    	croppedDocs.add(expansionDocuments.getHit(i));
		}
		return croppedDocs;
	}

	public SearchHits getExpansionDocuments() {
	    return getExpansionDocuments(expansionDocuments.size());
    }
	
	/**
	 * Compute the language model of the expansion documents
	 * @param expansionIndex The index used for expansion
	 * @return A FeatureVector with term probabilities based on <i>only</i> the expansion documents
	 */
	public FeatureVector expansionLanguageModel(IndexWrapper expansionIndex) {
		if (expansionLM == null) {
			// Add all of the FeatureVectors into an array so we can create a vocabulary
			FeatureVector[] vectors = new FeatureVector[expansionDocuments.size()];
			for (int i = 0; i < vectors.length; i++) {
				vectors[i] = expansionDocuments.getHit(i).getFeatureVector();
			}
			List<String> vocabulary = FeatureUtils.createVocabulary(vectors);
			
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
	
	/**
	 * Compute the language model of the original document, i.e. the document LM pre-expansion
	 * @param targetIndex The index containing the original document
	 * @return A FeatureVector with term probabilities based on <i>only</i> the original document
	 */
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
	
	/**
	 * See {@link #languageModel(IndexWrapper, IndexWrapper, double) languageModel}; use when the original and expansion documents come from the same index
	 * @param index The index of both the original and expansion documents
	 * @param originalLMWeight The weight of the original document language model; the expansion LM will be weighted 1-originalLMWeight
	 * @return A FeatureVector containing term probabilities of the expanded language model
	 */
	public FeatureVector languageModel(IndexWrapper index, double originalLMWeight) {
		return languageModel(index, index, originalLMWeight);
	}
	
	/**
	 * The expanded language model based on the combined original and expansion language models
	 * @param targetIndex The index containing the original document
	 * @param expansionIndex The index containing the expansion documents
	 * @param originalLMWeight The weight to give the original document language model; the expansion LM will be weighted 1-originalLMWeight
	 * @return A FeatureVector containing term probabilities of the expanded language model
	 */
	public FeatureVector languageModel(IndexWrapper targetIndex, IndexWrapper expansionIndex, double originalLMWeight) {
		FeatureVector origLM = originalLanguageModel(targetIndex);
		FeatureVector expansionLM = expansionLanguageModel(expansionIndex);
		
		List<String> vocabulary = FeatureUtils.createVocabulary(origLM, expansionLM);

		FeatureVector lm = new FeatureVector(null);
		vocabulary.stream().forEach(term -> {
			lm.addTerm(term, originalLMWeight * origLM.getFeatureWeight(term) + (1 - originalLMWeight) * expansionLM.getFeatureWeight(term));
		});
		return lm;
	}
	
}
