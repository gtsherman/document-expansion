package org.retrievable.document_expansion.features;

import java.util.Collections;
import java.util.List;
import java.util.function.ToDoubleBiFunction;

import cc.mallet.util.Maths;
import edu.gslis.textrepresentation.FeatureVector;

public class LMFeatures {

	/**
	 * Compare language models by KL-divergence
	 * @param lm1 The FeatureVector representing the first language model
	 * @param lm2 The FeatureVector representing the second language model
	 * @return The KL-divergence between the two models
	 */
	public static double languageModelsKL(FeatureVector lm1, FeatureVector lm2) {
		return compareLanguageModels(lm1, lm2, Maths::klDivergence);
	}
	
	/**
	 * Compare language models by Shannon-Jensen divergence
	 * @param lm1 The FeatureVector representing the first language model
	 * @param lm2 The FeatureVector representing the second language model
	 * @return The Shannon-Jensen divergence between the two models
	 */
	public static double languageModelsShannonJensen(FeatureVector lm1, FeatureVector lm2) {
		return compareLanguageModels(lm1, lm2, Maths::jensenShannonDivergence);
	}
	
	/**
	 * Compute the perplexity of a language model for some sample of text
	 * @param sample A vector containing term frequencies for the sample of text
	 * @param model A language model to be evaluated
	 * @return The perplexity of the language model
	 */
	public static double perplexity(FeatureVector sample, FeatureVector model) {
		double logLikelihood = sample.getFeatures().stream().mapToDouble(term -> {
			return sample.getFeatureWeight(term) * Math.log(model.getFeatureWeight(term) / Math.log(2));
		}).sum();
		double crossEntropy = (-1 / sample.getLength()) * logLikelihood;
		double perplexity = Math.pow(2, crossEntropy);
		return perplexity;
	}
		
	/**
	 * Use this to compute both KL and Shannon-Jensen, since they require the same steps
	 * @param lm1
	 * @param lm2
	 * @param comparisonMethod From Mallet, use Maths::klDivergence or Maths::jensenShannonDivergence
	 * @return The comparison metric (KL or SJ)
	 */
	private static double compareLanguageModels(FeatureVector lm1, FeatureVector lm2, ToDoubleBiFunction<double[], double[]> comparisonMethod) {
		List<String> vocabulary = FeatureUtils.createVocabulary(lm1, lm2);

		double[] p1 = getProbabilityVector(lm1, vocabulary);
		double[] p2 = getProbabilityVector(lm2, vocabulary);

		return comparisonMethod.applyAsDouble(p1, p2);
	}
	
	/**
	 * Convert a sparse vector to a full vocabulary vector
	 * @param lm The sparse vector, e.g. a document
	 * @param vocabulary The full sorted vocabulary
	 * @return The sparse vector as represented by a sorted full vocabulary vector
	 */
	private static double[] getProbabilityVector(FeatureVector lm, List<String> vocabulary) {
		// The probability vector for this language model is all 0.0 initially
		double[] probabilityVector = new double[vocabulary.size()];

		for (String term : lm) {
			// The point is to get perfectly comparable LM vectors. To do this, we have to first identify the index of the non-zero terms.
			int i = Collections.binarySearch(vocabulary, term);
			if (i < 0) {
				System.err.println("Term " + term + " is not in the vocabulary. Your vocabulary is incomplete. Skipping this term.");
			} else {
				// Set this term's spot in the probability vector (which has the same order as the vocabulary) equal to its probability
				probabilityVector[i] = lm.getFeatureWeight(term);
			}
		}
		return probabilityVector;
	}

}
