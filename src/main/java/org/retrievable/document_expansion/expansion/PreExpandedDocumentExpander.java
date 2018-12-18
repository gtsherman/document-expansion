package org.retrievable.document_expansion.expansion;

import java.util.Map;

import edu.gslis.utils.Stopper;
import org.retrievable.document_expansion.expansion.DocumentExpander;

import edu.gslis.indexes.IndexWrapper;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;

public class PreExpandedDocumentExpander extends DocumentExpander {
	
	private Map<Integer, SearchHits> preExpandedDocs;

	public PreExpandedDocumentExpander(IndexWrapper index, int numTerms, Stopper stopper, Map<Integer, SearchHits> preExpandedDocs) {
		super(index, numTerms, stopper);
		this.preExpandedDocs = preExpandedDocs;
	}
	
	@Override
	public SearchHits expandDocument(SearchHit document, int numDocs) {
		if (numDocs > maxNumDocs) {
			setMaxNumDocs(numDocs);
		}

		SearchHits expDocs = preExpandedDocs.get(document.getDocID());;
		if (expDocs == null) {
			System.err.println("No exp docs for " + document.getDocID());
			return new SearchHits();
		}
		expDocs.crop(numDocs);
		return expDocs;
	}

}
