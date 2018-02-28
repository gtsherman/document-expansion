package edu.gslis.evaluation.evaluators;

import edu.gslis.eval.Qrels;
import edu.gslis.queries.GQuery;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.searchhits.SearchHitsBatch;

import java.util.Iterator;
import java.util.Set;

public class NDCGEvaluator {

	/**
	 * Compute average nDCG@k across queries
	 * @param rankCutoff The k in nDCG@k
	 * @param batchResults Batch search results
	 * @param qrels Relevance judgments
	 * @return The average nDCG@k across queries
	 */
	public double averageNDCG(int rankCutoff, SearchHitsBatch batchResults, Qrels qrels) {
		double avgNDCG = 0.0;

		Iterator<String> queryIt = batchResults.queryIterator();
		while (queryIt.hasNext()) {
			String query = queryIt.next();
			SearchHits results = batchResults.getSearchHits(query);

			double ndcg = ndcg(rankCutoff, query, results, qrels);
			avgNDCG += ndcg;
		}
		avgNDCG /= batchResults.getNumQueries();
		
		return avgNDCG;
	}
	
	/**
	 * Compute nDCG@k for a single query
	 * @param rankCutoff The k in nDCG@k
	 * @param query The query to evaluate
	 * @param results The search results for this query
	 * @param qrels Relevance judgments
	 * @return The nDCG@k for the given query
	 */
	public double ndcg(int rankCutoff, GQuery query, SearchHits results, Qrels qrels) {
		return ndcg(rankCutoff, query.getTitle(), results, qrels);
	}

	/**
	 * Compute nDCG@k for a single query
	 * @param rankCutoff The k in nDCG@k
	 * @param query The query to evaluate
	 * @param results The search results for this query
	 * @param qrels Relevance judgments
	 * @return The nDCG@k for the given query
	 */
	public double ndcg(int rankCutoff, String query, SearchHits results, Qrels qrels) {
		double dcg = dcg(rankCutoff, query, results, qrels);
		double idcg = idcg(rankCutoff, query, qrels);
		if (idcg == 0) {
			System.err.println("No relevant documents for query "+query+"?");
			return 0;
		}
		return dcg / idcg;
	}
	
	/**
	 * Compute DCG@k for a single query
	 * @param rankCutoff The k in DCG@k
	 * @param query The query to evaluate
	 * @param results The search results for this query
	 * @param qrels Relevance judgments
	 * @return The DCG@k for the given query
	 */
	public double dcg(int rankCutoff, GQuery query, SearchHits results, Qrels qrels) {
		return dcg(rankCutoff, query.getTitle(), results, qrels);
	}
	
	/**
	 * Compute DCG@k for a single query
	 * @param rankCutoff The k in DCG@k
	 * @param query The query to evaluate
	 * @param results The search results for this query
	 * @param qrels Relevance judgments
	 * @return The DCG@k for the given query
	 */
	public double dcg(int rankCutoff, String query, SearchHits results, Qrels qrels) {
		double dcg = 0.0;
		for (int i = 1; i <= rankCutoff; i++) {
			SearchHit hit = results.getHit(i-1);
			int rel = qrels.getRelLevel(query, hit.getDocno());
			dcg += dcgAtRank(i, rel);
		}
		return dcg;
	}
	
	/**
	 * Compute ideal DCG@k for a single query
	 * @param rankCutoff The k in DCG@k
	 * @param query The query to evaluate
	 * @param qrels Relevance judgments
	 * @return The iDCG@k for the given query
	 */
	public double idcg(int rankCutoff, GQuery query, Qrels qrels) {
		return idcg(rankCutoff, query.getTitle(), qrels);
	}
	
	/**
	 * Compute ideal DCG@k for a single query
	 * @param rankCutoff The k in DCG@k
	 * @param query The query to evaluate
	 * @param qrels Relevance judgments
	 * @return The iDCG@k for the given query
	 */
	public double idcg(int rankCutoff, String query, Qrels qrels) {
		SearchHits idealResults = new SearchHits();
		
		Set<String> relDocs = qrels.getRelDocs(query);

		if (relDocs == null) {
			return dcg(rankCutoff, query, idealResults, qrels);
		}

		for (String doc : relDocs) {
			int relLevel = qrels.getRelLevel(query, doc);

			SearchHit hit = new SearchHit();
			hit.setDocno(doc);
			hit.setScore(relLevel);
			idealResults.add(hit);
		}
		
		idealResults.rank();
		
		return dcg(rankCutoff, query, idealResults, qrels);
	}
	
	private double dcgAtRank(int rank, int rel) {
		return (Math.pow(2, rel) - 1)/(Math.log(rank+1));
	}

}
