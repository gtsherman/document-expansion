package edu.gslis.indexes;

import java.util.concurrent.ExecutionException;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import edu.gslis.textrepresentation.FeatureVector;
import edu.gslis.textrepresentation.IndriDocument;
import edu.gslis.utils.Stopper;
import lemurproject.indri.QueryEnvironment;

public class CachedFeatureVectorIndexWrapperIndriImpl extends IndexWrapperIndriImpl {
	
	private LoadingCache<Integer, FeatureVector> featureVectors = CacheBuilder.newBuilder()
			.maximumSize(10000)
			.build(
					new CacheLoader<Integer, FeatureVector>() {
						public FeatureVector load(Integer docId) throws Exception {
							return getDocVectorFromIndex(docId, null);
						}
					});	

	public CachedFeatureVectorIndexWrapperIndriImpl(String pathToIndex) {
		super(pathToIndex);
	}
	
	public FeatureVector getDocVector(int docID, Stopper stopper) {
		FeatureVector vector;
		try {
			vector =  featureVectors.get(docID);
		} catch (ExecutionException e) {
			System.err.println("Error getting feature vector from cache. Getting from index.");
			vector = getDocVectorFromIndex(docID, null);
		}
		
		if (stopper != null) {
			vector.applyStopper(stopper);
		}
		
		return vector;
	}

	public FeatureVector getDocVector(String docno, Stopper stopper) {
		return getDocVector(getDocId(docno), stopper);
	}
	
	private FeatureVector getDocVectorFromIndex(int docID, Stopper stopper) {
		IndriDocument doc = new IndriDocument((QueryEnvironment)super.getActualIndex());
		return doc.getFeatureVector(docID, stopper);
	}

}
