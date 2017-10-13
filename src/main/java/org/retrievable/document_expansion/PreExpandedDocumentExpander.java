package org.retrievable.document_expansion;

import java.util.Map;

import org.retrievable.document_expansion.data.ExpandedDocument;

import edu.gslis.indexes.IndexWrapper;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;

public class PreExpandedDocumentExpander extends DocumentExpander {
	
	private Map<String, SearchHits> preExpandedDocs;
	private int numDocs;
	
	public PreExpandedDocumentExpander(IndexWrapper index, Map<String, SearchHits> preExpandedDocs, int numDocs) {
		super(index);
		this.preExpandedDocs = preExpandedDocs;
		this.numDocs = numDocs;
	}
	
	@Override
	public ExpandedDocument expandDocument(SearchHit document) {
		SearchHits expDocs = preExpandedDocs.get(document.getDocno());;
		if (expDocs == null) {
			System.err.println("No exp docs for " + document.getDocno());
			return new ExpandedDocument(document, new SearchHits());
		}
		expDocs.crop(numDocs);
		normalizeScores(expDocs);
		return new ExpandedDocument(document, expDocs);
	}

}
