package org.retrievable.document_expansion.scoring;

import com.google.common.collect.Streams;
import edu.gslis.docscoring.support.IndexBackedCollectionStats;
import edu.gslis.scoring.CachedDocScorer;
import edu.gslis.scoring.DirichletDocScorer;
import edu.gslis.scoring.DocScorer;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import org.retrievable.document_expansion.expansion.DocumentExpander;

public class ExpansionDocScorer implements DocScorer {
	
	DocumentExpander docExpander;
	DocScorerWithNormalizedScorePrior expScorer;

	private int numDocs = 5;
	

	public ExpansionDocScorer(DocumentExpander docExpander) {
		this(2500, docExpander);
	}

	public ExpansionDocScorer(DocumentExpander docExpander, int numDocs) {
		this(docExpander);
		setNumDocs(numDocs);
	}
	
	public ExpansionDocScorer(double mu, DocumentExpander docExpander) {
		this.docExpander = docExpander;

		IndexBackedCollectionStats collectionStats = new IndexBackedCollectionStats();
		collectionStats.setStatSource(docExpander.getIndex());

		DocScorer dirichletScorer = new CachedDocScorer(new DirichletDocScorer(mu, collectionStats));
		expScorer = new DocScorerWithNormalizedScorePrior(dirichletScorer);
	}

	public ExpansionDocScorer(double mu, DocumentExpander docExpander, int numDocs) {
	    this(mu, docExpander);
		setNumDocs(numDocs);
	}
	
	@Override
	public double scoreTerm(String term, SearchHit document) {
		SearchHits expansionDocs = getExpansionDocs(document);
		expScorer.setDocuments(expansionDocs);

		double total = Streams.stream(expansionDocs).mapToDouble(doc -> expScorer.scoreTerm(term, doc)).sum();
		
		return total;
	}

	public void setNumDocs(int numDocs) {
		this.numDocs = numDocs;
	}
	
	public SearchHits getExpansionDocs(SearchHit document) {
		return docExpander.expandDocument(document, numDocs);
	}

}
