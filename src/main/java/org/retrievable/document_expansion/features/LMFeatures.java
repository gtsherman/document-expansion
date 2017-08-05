package org.retrievable.document_expansion.features;

import java.util.Iterator;
import java.util.TreeSet;
import java.util.function.ToDoubleBiFunction;

import edu.gslis.textrepresentation.FeatureVector;

public class LMFeatures {
	
	/**
	 * Compare language models, most likely by KL-divergence or Shannon-Jensen divergence.
	 * @param lm1 The FeatureVector representing the first language model
	 * @param lm2 The FeatureVector representing the second language model
	 * @param comparisonMethod A comparison method taking two arrays of probabilities as its input
	 * @return The comparison metric
	 */
	public static double compareLanguageModels(FeatureVector lm1, FeatureVector lm2, ToDoubleBiFunction<double[], double[]> comparisonMethod) {
		TreeSet<String> vocabulary = FeatureUtils.createVocabulary(lm1, lm2);

		double[] p1 = getProbabilityVector(lm1, vocabulary);
		double[] p2 = getProbabilityVector(lm2, vocabulary);

		return comparisonMethod.applyAsDouble(p1, p2);
	}
	
	private static double[] getProbabilityVector(FeatureVector lm, TreeSet<String> vocabulary) {
		double[] probabilityVector = new double[vocabulary.size()];
		int i = 0;
		Iterator<String> terms = vocabulary.iterator();
		while (terms.hasNext()) {
			probabilityVector[i] = lm.getFeatureWeight(terms.next());
			i++;
		}
		return probabilityVector;
	}

}
