package org.retrievable.document_expansion.scoring;

import edu.gslis.scoring.DocScorer;
import edu.gslis.scoring.DocScorerWithDocumentPrior;
import edu.gslis.searchhits.SearchHit;

/**
 * A DocScorerWithDocumentPrior where the document prior is whatever is already saved in the SearchHit. 
 * In other words, the prior is the result of document.getScore()
 * 
 * @author Garrick
 *
 */
public class DocScorerWithStoredScorePrior extends DocScorerWithDocumentPrior {
	
	public DocScorerWithStoredScorePrior(DocScorer nonPriorScorer) {
		super(nonPriorScorer);
	}

	@Override
	public double getPrior(SearchHit document) {
		return document.getScore();
	}

}
