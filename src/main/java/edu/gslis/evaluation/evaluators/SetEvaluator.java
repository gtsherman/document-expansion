package edu.gslis.evaluation.evaluators;

import edu.gslis.eval.Qrels;
import edu.gslis.queries.GQuery;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;

import java.util.HashSet;
import java.util.Set;

public class SetEvaluator {

    public double precision(GQuery query, SearchHits results, Qrels qrels) {
        return precision(query.getTitle(), results, qrels);
    }

    public double precision(String query, SearchHits results, Qrels qrels) {
        return precisionAt(results.size(), query, results, qrels);
    }

    public double precisionAt(int k, GQuery query, SearchHits results, Qrels qrels) {
        return precisionAt(k, query.getTitle(), results, qrels);
    }

    public double precisionAt(int k, String query, SearchHits results, Qrels qrels) {
        results.rank();

        int relevant = 0;
        for (int i = 0; i < k; i++) {
            SearchHit hit = results.getHit(i);
            int rel = qrels.isRel(query, hit.getDocno()) ? 1 : 0;
            relevant += rel;
        }

        return relevant / (double) k;
    }

    public double recall(GQuery query, SearchHits results, Qrels qrels) {
        return recall(query.getTitle(), results, qrels);
    }

    public double recall(String query, SearchHits results, Qrels qrels) {
        return recallAt(results.size(), query, results, qrels);
    }

    public double recallAt(int k, GQuery query, SearchHits results, Qrels qrels) {
        return recallAt(k, query.getTitle(), results, qrels);
    }

    public double recallAt(int k, String query, SearchHits results, Qrels qrels) {
        results.rank();

        int relevant = 0;
        for (int i = 0; i < k; i++) {
            SearchHit hit = results.getHit(i);
            int rel = qrels.isRel(query, hit.getDocno()) ? 1 : 0;
            relevant += rel;
        }

        double judgedRelevant = qrels.numRel(query);
        if (judgedRelevant == 0.0) {
            System.err.println("No judged relevant documents for query: " + query);
            return 0;
        }

        return relevant / judgedRelevant;
    }

    public <T> double jaccardSimilarity(Set<T> a, Set<T> b) {
        Set<T> intersection = new HashSet<>(a);
        intersection.retainAll(b);

        Set<T> union = new HashSet<>(a);
        union.addAll(b);

        return intersection.size() / (double) union.size();
    }

}
