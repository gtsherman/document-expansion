package org.retrievable.document_expansion.lms;

import com.google.common.collect.Streams;
import edu.gslis.docscoring.support.CollectionStats;
import edu.gslis.docscoring.support.IndexBackedCollectionStats;
import edu.gslis.indexes.IndexWrapper;
import edu.gslis.scoring.DirichletDocScorer;
import edu.gslis.scoring.DocScorer;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.textrepresentation.FeatureVector;
import org.retrievable.document_expansion.features.FeatureUtils;
import org.retrievable.document_expansion.scoring.ExpansionDocScorer;

import java.util.*;

public class LanguageModelEstimator {

	/**
	 * Compute the language model of the expansion documents
	 * @param originalDocument The original document to be expanded
	 * @param scorer An ExpansionDocScorer instance that is already configured with the correct number of expansion documents, etc.
	 * @return A FeatureVector with term probabilities based on all of the terms in <i>only</i> the expansion documents
	 */
	public static FeatureVector expansionLanguageModel(SearchHit originalDocument, ExpansionDocScorer scorer) {
		return expansionLanguageModel(originalDocument, scorer, null);
	}

	/**
	 * Compute the language model of the expansion documents
     * @param originalDocument The original document to be expanded
	 * @param scorer An ExpansionDocScorer instance that is already configured with the correct number of expansion documents, etc.
     * @param includedTerms Only estimate the probabilities of the included terms. If null, will estimate the probability of all terms in the expansion documents
	 * @return A FeatureVector with term probabilities based on <i>only</i> the expansion documents
	 */
	public static FeatureVector expansionLanguageModel(SearchHit originalDocument, ExpansionDocScorer scorer, Set<String> includedTerms) {
	    SearchHits expansionDocuments = scorer.getExpansionDocs(originalDocument);

		List<String> vocabulary;
	    if (includedTerms != null) {
			vocabulary = new ArrayList<>(includedTerms);
			Collections.sort(vocabulary);
		} else {
	    	// Add all of the FeatureVectors into an array so we can create a vocabulary
            FeatureVector[] vectors = new FeatureVector[expansionDocuments.size()];
            for (int i = 0; i < vectors.length; i++) {
                vectors[i] = expansionDocuments.getHit(i).getFeatureVector();
            }
            vocabulary = FeatureUtils.createVocabulary(vectors);
		}

        // Create the language model
        FeatureVector expansionLM = new FeatureVector(null);
        vocabulary.stream().forEach(term -> {
            expansionLM.addTerm(term, scorer.scoreTerm(term, originalDocument));
        });

        // Clear the individual feature vectors because they are serious memory hogs
        Streams.stream(expansionDocuments).forEach(doc -> doc.setFeatureVector(null));

		return expansionLM;
	}
	
	/**
	 * Compute the language model of the original document, i.e. the document LM pre-expansion
	 * @param document The origianl document to be expanded
	 * @param targetIndex The index containing the original document
	 * @return A FeatureVector with term probabilities based on <i>only</i> the original document
	 */
	public static FeatureVector languageModel(SearchHit document, IndexWrapper targetIndex) {
        IndexBackedCollectionStats collectionStats = new IndexBackedCollectionStats();
        collectionStats.setStatSource(targetIndex);

        return languageModel(document, collectionStats);
    }

    public static FeatureVector languageModel(SearchHit document, CollectionStats collectionStats) {
		DocScorer scorer = new DirichletDocScorer(collectionStats);

		return languageModel(document, scorer);
	}

	public static FeatureVector languageModel(SearchHit document, DocScorer scorer) {
		FeatureVector originalLM = new FeatureVector(null);

		for (String term : document.getFeatureVector().getFeatures()) {
			originalLM.addTerm(term, scorer.scoreTerm(term, document));
		}
		return originalLM;
	}
	
	public static FeatureVector combinedLanguageModel(FeatureVector lm1, FeatureVector lm2, double lm1Weight) {
		List<String> vocabulary = FeatureUtils.createVocabulary(lm1, lm2);

		FeatureVector lm = new FeatureVector(null);
		vocabulary.stream().forEach(term -> {
			lm.addTerm(term, lm1Weight * lm1.getFeatureWeight(term) + (1 - lm1Weight) * lm2.getFeatureWeight(term));
		});
		return lm;
	}
	
}
