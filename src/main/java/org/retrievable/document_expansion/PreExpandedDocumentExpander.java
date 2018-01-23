package org.retrievable.document_expansion;

import java.util.Map;

import org.retrievable.document_expansion.data.ExpandedDocument;

import edu.gslis.indexes.IndexWrapper;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;

public class PreExpandedDocumentExpander extends DocumentExpander {
	
	private Map<Integer, SearchHits> preExpandedDocs;
	private int numDocs;
	
	public PreExpandedDocumentExpander(IndexWrapper index, Map<Integer, SearchHits> preExpandedDocs, int numDocs) {
		super(index);
		this.preExpandedDocs = preExpandedDocs;
		this.numDocs = numDocs;
	}
	
	@Override
	public ExpandedDocument expandDocument(SearchHit document) {
		SearchHits expDocs = preExpandedDocs.get(document.getDocID());;
		if (expDocs == null) {
			System.err.println("No exp docs for " + document.getDocID());
			return new ExpandedDocument(document, new SearchHits());
		}
		expDocs.crop(numDocs);
		return new ExpandedDocument(document, expDocs);
	}

}
