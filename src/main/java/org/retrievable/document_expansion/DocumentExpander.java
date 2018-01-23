package org.retrievable.document_expansion;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import edu.gslis.indexes.IndexWrapper;
import edu.gslis.queries.GQuery;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.utils.Stopper;
import org.retrievable.document_expansion.data.ExpandedDocument;

import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

public class DocumentExpander {
	
	private int numTerms;
	private int maxNumTerms;
	private Stopper stopper;
	private IndexWrapper index;

	private LoadingCache<SearchHit, ExpandedDocument> expandedDocs = CacheBuilder.newBuilder()
			.softValues()
			.build(
					new CacheLoader<SearchHit, ExpandedDocument>() {
						public ExpandedDocument load(SearchHit document) {
							return expandDocumentByRetrieval(document, maxNumTerms);
						}
					});

	public DocumentExpander(IndexWrapper index) {
		this(index, null);
	}
	
	public DocumentExpander(IndexWrapper index, Stopper stopper) {
		this(index, 20, stopper);
	}

	public DocumentExpander(IndexWrapper index, int numTerms, Stopper stopper) {
		setNumTerms(numTerms);
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
	
	public void setNumTerms(int numTerms) {
		this.numTerms = numTerms;
	}

	public void setMaxNumTerms(int maxNumTerms) {
		this.maxNumTerms = maxNumTerms;
	}
	
	public void addExpansionDocument(SearchHit originalDocument, SearchHit... expansionDocuments) {
		SearchHits expansionDocs = new SearchHits();
		Stream.of(expansionDocuments).forEach(expansionDocs::add);
		addExpansionDocument(originalDocument, expansionDocs);
	}
	
	public void addExpansionDocument(SearchHit originalDocument, SearchHits expansionDocuments) {
		addExpansionDocument(new ExpandedDocument(originalDocument, expansionDocuments));
	}
	
	public void addExpansionDocument(ExpandedDocument expandedDocument) {
		expandedDocs.put(expandedDocument.getOriginalDocument(), expandedDocument);
	}
	
	public ExpandedDocument expandDocument(SearchHit document) {
		try {
            return expandedDocs.get(document);
		} catch (ExecutionException e) {
			System.err.println("Error getting expanded document " + document.getDocno() + " from the cache.");
			e.printStackTrace(System.err);
			return new ExpandedDocument(document, new SearchHits());
		}
	}
	
	public ExpandedDocument expandDocumentByRetrieval(SearchHit document, int numDocs) {
		GQuery pseudoQuery = createDocumentPseudoQuery(document);

		SearchHits expansionDocs = index.runQuery(pseudoQuery, numDocs);

		return new ExpandedDocument(document, expansionDocs);
	}

	public GQuery createDocumentPseudoQuery(SearchHit document) {
		GQuery docPseudoQuery = new GQuery();
		docPseudoQuery.setFeatureVector(document.getFeatureVector());
		if (stopper != null) {
			docPseudoQuery.applyStopper(stopper);
		}
		docPseudoQuery.getFeatureVector().clip(numTerms);

		return docPseudoQuery;
	}

}
