package org.retrievable.document_expansion.features;

import java.util.TreeSet;
import java.util.stream.Stream;

import edu.gslis.textrepresentation.FeatureVector;

public class FeatureUtils {
	
	public static TreeSet<String> createVocabulary(FeatureVector... sparseVectors) {
		TreeSet<String> vocabulary = new TreeSet<String>();
		Stream.of(sparseVectors).forEach(vector -> vocabulary.addAll(vector.getFeatures()));
		return vocabulary;
	}

}
