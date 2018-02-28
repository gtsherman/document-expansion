package edu.gslis.evaluation.evaluators;

import edu.gslis.eval.Qrels;
import edu.gslis.queries.GQuery;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.searchhits.SearchHitsBatch;

import java.util.Iterator;

public class MAPEvaluator {
	
	public double meanAveragePrecision(SearchHitsBatch batchResults, Qrels qrels) {
		double map = 0.0;

		Iterator<String> queryIt = batchResults.queryIterator();
		while (queryIt.hasNext()) {
			String query = queryIt.next();
			SearchHits results = batchResults.getSearchHits(query);
			double ap = averagePrecision(query, results, qrels);
			map += ap;
		}
		
		map /= batchResults.getNumQueries();
		return map;
	}
	
	public double averagePrecision(GQuery query, SearchHits results, Qrels qrels) {
		return averagePrecision(query.getTitle(), results, qrels);
	}
	
	public double averagePrecision(String query, SearchHits results, Qrels qrels) {
		results.rank();

		double ap = 0.0;

		int rels = 0;
		int seen = 0;

		for (SearchHit result : results) {
			seen++;
			
			if (qrels.isRel(query, result.getDocno())) {
				rels++;
				ap += rels / (double)seen;
			}
		}
	
		try {
			ap /= qrels.getRelDocs(query).size();
		} catch (NullPointerException e) {
			System.err.println("Error with AP division for "+query+".)");
			System.err.println("AP="+ap);
			if (ap > 0) {
				System.err.println("Somehow we have seen rel docs but don't have rel docs now.");
			}
			e.printStackTrace(System.err);
		}

		if (Double.isNaN(ap))
			ap = 0.0;
		return ap;
	}
}
