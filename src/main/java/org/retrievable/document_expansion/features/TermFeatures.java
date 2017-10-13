package org.retrievable.document_expansion.features;

import edu.gslis.docscoring.support.CollectionStats;
import edu.gslis.textrepresentation.FeatureVector;

public class TermFeatures {
	
	public static double idf(CollectionStats collectionStats, String term) {
		double N = collectionStats.getDocCount();
		double n = collectionStats.docCount(term);
		double idf = Math.log((N - n + 0.5) / (n + 0.5));
		return idf;
	}
	
	public static double[] idf(CollectionStats collectionStats, String... terms) {
		double[] idfs = new double[terms.length];
		for (int i = 0; i < terms.length; i++) {
			idfs[i] = idf(collectionStats, terms[i]);
		}
		return idfs;
	}

}
