package org.retrievable.document_expansion.expansion;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import edu.gslis.indexes.IndexWrapper;
import edu.gslis.queries.GQuery;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.textrepresentation.FeatureVector;
import edu.gslis.utils.Stopper;

import java.util.concurrent.ExecutionException;

public class DocumentExpander {
	
	private int numTerms;
	private int maxNumDocs;
	private Stopper stopper;
	private IndexWrapper index;

	private LoadingCache<SearchHit, SearchHits> expandedDocs = CacheBuilder.newBuilder()
			.softValues()
			.build(
					new CacheLoader<SearchHit, SearchHits>() {
						public SearchHits load(SearchHit document) {
							return expandDocumentByRetrieval(document, maxNumDocs);
						}
					});

	public DocumentExpander(IndexWrapper index) {
		this(index, null);
	}
	
	public DocumentExpander(IndexWrapper index, Stopper stopper) {
		this(index, 20, stopper);
	}

	public DocumentExpander(IndexWrapper index, int numTerms, Stopper stopper) {
	    this.numTerms = numTerms;
		this.stopper = stopper;
		setIndex(index);
	}

	public IndexWrapper getIndex() {
		return index;
	}

	public void setIndex(IndexWrapper index) {
		this.index = index;
	}

	public int getNumTerms() {
		return numTerms;
	}

	public void setMaxNumDocs(int maxNumDocs) {
		this.maxNumDocs = maxNumDocs;
	}
	
	public SearchHits expandDocument(SearchHit document) {
	    return expandDocument(document, maxNumDocs);
	}

	public SearchHits expandDocument(SearchHit document, int numDocs) {
		try {
            SearchHits expansionDocuments = expandedDocs.get(document);
            return croppedHits(expansionDocuments, numDocs);
		} catch (ExecutionException e) {
			System.err.println("Error getting expanded document " + document.getDocno() + " from the cache.");
			e.printStackTrace(System.err);
			return new SearchHits();
		}
	}

	private SearchHits croppedHits(SearchHits hits, int limit) {
		SearchHits croppedDocs = new SearchHits();
		for (int i = 0; i < Math.min(limit, hits.size()); i++) {
			croppedDocs.add(hits.getHit(i));
		}
		return croppedDocs;
	}

	public SearchHits expandDocumentByRetrieval(SearchHit document, int numDocs) {
		GQuery pseudoQuery = createDocumentPseudoQuery(document);

		SearchHits expansionDocs = index.runQuery(pseudoQuery, numDocs);

		return expansionDocs;
	}

	public GQuery createDocumentPseudoQuery(SearchHit document) {
		GQuery docPseudoQuery = new GQuery();

		// We need to copy the document vector so we don't cause mutations with side effects when we clip it
		FeatureVector docVector = document.getFeatureVector().deepCopy();
		docPseudoQuery.setFeatureVector(docVector);

		if (stopper != null) {
			docPseudoQuery.applyStopper(stopper);
		}
		docPseudoQuery.getFeatureVector().clip(numTerms);

		return docPseudoQuery;
	}

}
