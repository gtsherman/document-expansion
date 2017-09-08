package org.retrievable.document_expansion;

import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import org.retrievable.document_expansion.data.ExpandedDocument;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Streams;

import edu.gslis.indexes.IndexWrapper;
import edu.gslis.queries.GQuery;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.utils.Stopper;

public class DocumentExpander {
	
	private int numTerms;
	private int numDocs;
	private Stopper stopper;
	private IndexWrapper index;
	
	private LoadingCache<SearchHit, ExpandedDocument> expandedDocs = CacheBuilder.newBuilder()
			.softValues()
			.build(
					new CacheLoader<SearchHit, ExpandedDocument>() {
						public ExpandedDocument load(SearchHit document) throws Exception {
							return expandDocumentByRetrieval(document);
						}
					});		

	public DocumentExpander(IndexWrapper index) {
		this(index, null);
	}
	
	public DocumentExpander(IndexWrapper index, Stopper stopper) {
		this(index, 20, 10, stopper);
	}

	public DocumentExpander(IndexWrapper index, int numTerms, int numDocs, Stopper stopper) {
		setNumTerms(numTerms);
		setNumDocs(numDocs);
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
	
	public int getNumDocs() {
		return numDocs;
	}
	
	public void setNumDocs(int numDocs) {
		this.numDocs = numDocs;
	}
	
	public void addExpansionDocument(SearchHit originalDocument, SearchHit... expansionDocuments) {
		SearchHits expansionDocs = new SearchHits();
		Stream.of(expansionDocuments).forEach(doc -> expansionDocs.add(doc));
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
	
	public ExpandedDocument expandDocumentByRetrieval(SearchHit document) {
		GQuery pseudoQuery = createDocumentPseudoQuery(document);

		// Find expansion docs
		SearchHits expansionDocs = index.runQuery(pseudoQuery, numDocs);
		if (stopper != null) {
			Streams.stream(expansionDocs).forEach(doc -> {
				doc.getFeatureVector().applyStopper(stopper);
			});
		}
		normalizeScores(expansionDocs);
		
		return new ExpandedDocument(document, expansionDocs);
	}
	
	private GQuery createDocumentPseudoQuery(SearchHit document) {
		GQuery docPseudoQuery = new GQuery();
		docPseudoQuery.setFeatureVector(document.getFeatureVector());
		if (stopper != null) {
			docPseudoQuery.applyStopper(stopper);
		}
		docPseudoQuery.getFeatureVector().clip(numTerms);
		docPseudoQuery.getFeatureVector().normalize();
		
		return docPseudoQuery;
	}
	
	private void normalizeScores(SearchHits expansionDocs) {
		// Get the total
		double total = 0.0;
		for (SearchHit doc : expansionDocs) {
			total += doc.getScore();
		}
		
		// Normalize the scores
		for (SearchHit doc : expansionDocs) {
			doc.setScore(doc.getScore() / total);
		}
	}
	
}
