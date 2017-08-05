package org.retrievable.document_expansion.scoring;

import java.util.concurrent.ExecutionException;

import org.retrievable.document_expansion.DocumentExpander;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Streams;

import edu.gslis.docscoring.support.IndexBackedCollectionStats;
import edu.gslis.scoring.CachedDocScorer;
import edu.gslis.scoring.DirichletDocScorer;
import edu.gslis.scoring.DocScorer;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;

public class ExpansionDocScorer implements DocScorer {
	
	DocumentExpander docExpander;

	DocScorer dirichletScorer;
	
	private LoadingCache<SearchHit, SearchHits> expansionDocs = CacheBuilder.newBuilder()
			.softValues()
			.build(
					new CacheLoader<SearchHit, SearchHits>() {
						public SearchHits load(SearchHit document) throws Exception {
							return getExpansionDocs(document);
						}
					});	
	
	public ExpansionDocScorer(DocumentExpander docExpander) {
		this(2500, docExpander);
	}
	
	public ExpansionDocScorer(double mu, DocumentExpander docExpander) {		
		this.docExpander = docExpander;
		
		IndexBackedCollectionStats collectionStats = new IndexBackedCollectionStats();
		collectionStats.setStatSource(docExpander.getIndex());

		this.dirichletScorer = new CachedDocScorer(new DirichletDocScorer(mu, collectionStats));
	}
	
	@Override
	public double scoreTerm(String term, SearchHit document) {
		SearchHits expansionDocs;
		try {
			expansionDocs = this.expansionDocs.get(document);
		} catch (ExecutionException e) {
			System.err.println("Error getting expansion documents for " + document.getDocno());
			e.printStackTrace(System.err);
			expansionDocs = new SearchHits();
		}
		
		double total = Streams.stream(expansionDocs)
				.mapToDouble(doc -> {
					DocScorer expScorer = new DocScorerWithDoNothingPrior(dirichletScorer);
					return expScorer.scoreTerm(term, doc);
				}).sum();
		
		return total;
	}
	
	public SearchHits getExpansionDocs(SearchHit document) {
		return docExpander.expandDocument(document).getExpansionDocuments();
	}

}
