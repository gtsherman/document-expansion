package org.retrievable.document_expansion.features;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import edu.gslis.textrepresentation.FeatureVector;

public class FeatureUtils {
	
	/**
	 * Get a sorted List of every term in the vocabulary, as contained by distinct feature vectors, i.e.
	 * the sorted union of the terms in each document's sparse FeatureVector.
	 * @param sparseVectors Any number of distinct feature vectors containing some subset of the vocabulary
	 * @return A sorted List of unique vocabulary terms
	 */
	public static List<String> createVocabulary(FeatureVector... sparseVectors) {
		// Collect the terms from each vector
		Set<String> vocabulary = new HashSet<String>();
		Stream.of(sparseVectors).forEach(vector -> vocabulary.addAll(vector.getFeatures()));
		
		// Sort the vocabulary
		List<String> sortedVocabulary = new ArrayList<String>(vocabulary);
		Collections.sort(sortedVocabulary);

		return sortedVocabulary;
	}
	
}
