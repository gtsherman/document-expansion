package org.retrievable.document_expansion.scoring;

import edu.gslis.scoring.DocScorer;
import edu.gslis.scoring.DocScorerWithDocumentPrior;
import edu.gslis.searchhits.SearchHit;

public class DocScorerWithDoNothingPrior extends DocScorerWithDocumentPrior {
	
	public DocScorerWithDoNothingPrior(DocScorer nonPriorScorer) {
		super(nonPriorScorer);
	}

	@Override
	public double getPrior(SearchHit document) {
		System.err.println("Prior score of document (" + document.getDocno() + "): " + document.getScore());
		return document.getScore();
	}

}
