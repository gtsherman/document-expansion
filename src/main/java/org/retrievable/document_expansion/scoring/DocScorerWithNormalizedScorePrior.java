package org.retrievable.document_expansion.scoring;

import edu.gslis.scoring.DocScorer;
import edu.gslis.scoring.DocScorerWithDocumentPrior;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;

import java.util.HashMap;
import java.util.Map;

public class DocScorerWithNormalizedScorePrior extends DocScorerWithDocumentPrior {

    private Map<SearchHit, Double> precomputedPriors = new HashMap<>();

    public DocScorerWithNormalizedScorePrior(DocScorer nonPriorScorer) {
        super(nonPriorScorer);
    }

    public void setDocuments(SearchHits documents) {
        precomputedPriors = normalizeScores(documents);
    }

    private Map<SearchHit, Double> normalizeScores(SearchHits expansionDocs) {
        Map<SearchHit, Double> normalizedScores = new HashMap<>();

        double k = expansionDocs.getHit(0).getScore();
        double sum = 0;
        for (SearchHit doc : expansionDocs) {
            double newscore = Math.exp(doc.getScore() - k);
            normalizedScores.put(doc, newscore);
            sum += newscore;
        }
        for (SearchHit doc : expansionDocs) {
            normalizedScores.put(doc, normalizedScores.get(doc) / sum);
        }

        return normalizedScores;
    }

    @Override
    public double getPrior(SearchHit document) {
        return precomputedPriors.getOrDefault(document, 1.0);
    }

}
