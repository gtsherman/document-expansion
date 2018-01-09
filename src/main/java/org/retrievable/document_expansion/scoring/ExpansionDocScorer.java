package org.retrievable.document_expansion.scoring;

import org.retrievable.document_expansion.DocumentExpander;

import com.google.common.collect.Streams;

import edu.gslis.docscoring.support.IndexBackedCollectionStats;
import edu.gslis.scoring.CachedDocScorer;
import edu.gslis.scoring.DirichletDocScorer;
import edu.gslis.scoring.DocScorer;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;

public class ExpansionDocScorer implements DocScorer {
	
	DocumentExpander docExpander;

	DocScorer expScorer;
	
	/*private LoadingCache<SearchHit, SearchHits> expansionDocs = CacheBuilder.newBuilder()
			.softValues()
			.build(
					new CacheLoader<SearchHit, SearchHits>() {
						public SearchHits load(SearchHit document) throws Exception {
							return docExpander.expandDocument(document).getExpansionDocuments();
						}
					});	*/
	
	public ExpansionDocScorer(DocumentExpander docExpander) {
		this(2500, docExpander);
	}
	
	public ExpansionDocScorer(double mu, DocumentExpander docExpander) {		
		this.docExpander = docExpander;
		
		IndexBackedCollectionStats collectionStats = new IndexBackedCollectionStats();
		collectionStats.setStatSource(docExpander.getIndex());

		DocScorer dirichletScorer = new CachedDocScorer(new DirichletDocScorer(mu, collectionStats));
		expScorer = new DocScorerWithStoredScorePrior(dirichletScorer);
	}
	
	@Override
	public double scoreTerm(String term, SearchHit document) {
		SearchHits expansionDocs = getExpansionDocs(document);

		double total = Streams.stream(expansionDocs)
				.mapToDouble(doc -> {
					return expScorer.scoreTerm(term, doc);
				}).sum();
		
		return total;
	}
	
	public SearchHits getExpansionDocs(SearchHit document) {
		/*try {
			return expansionDocs.get(document);
		} catch (ExecutionException e) {
			System.err.println("Error getting expansion documents for " + document.getDocno());
			e.printStackTrace(System.err);
			return new SearchHits();
		}*/
		return docExpander.expandDocument(document).getExpansionDocuments();
	}

}
