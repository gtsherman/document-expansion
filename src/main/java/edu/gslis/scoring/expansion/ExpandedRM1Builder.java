package edu.gslis.scoring.expansion;

import edu.gslis.docscoring.support.CollectionStats;
import edu.gslis.scoring.DirichletDocScorer;
import edu.gslis.scoring.DocScorer;
import edu.gslis.scoring.InterpolatedDocScorer;
import org.retrievable.document_expansion.expansion.DocumentExpander;
import org.retrievable.document_expansion.scoring.ExpansionDocScorer;

import java.util.HashMap;
import java.util.Map;

public class ExpandedRM1Builder extends StandardRM1Builder {

    private DocumentExpander documentExpander;
    private int numExpansionDocs;
    private double originalLMWeight = 1.0; // standard RM1 by default

    public ExpandedRM1Builder(CollectionStats collectionStats, DocumentExpander documentExpander, int numExpansionDocs) {
        this(StandardRM1Builder.DEFAULT_FEEDBACK_DOCS, StandardRM1Builder.DEFAULT_FEEDBACK_TERMS, collectionStats, documentExpander, numExpansionDocs);
    }

    public ExpandedRM1Builder(int feedbackDocs, int feedbackTerms, CollectionStats collectionStats, DocumentExpander documentExpander, int numExpansionDocs) {
        super(feedbackDocs, feedbackTerms, collectionStats);
        this.documentExpander = documentExpander;
        this.numExpansionDocs = numExpansionDocs;
    }

    public void setOriginalLMWeight(double originalLMWeight) {
        // Check whether we're actually changing the value of originalLMWeight and only recreate the doc scorers if so
        if (this.originalLMWeight != originalLMWeight) {
            this.originalLMWeight = originalLMWeight;
            createDocScorers();
        }
    }

    @Override
    protected void createDocScorers() {
        Map<DocScorer, Double> mu2500Scorers = new HashMap<>();
        mu2500Scorers.put(new DirichletDocScorer(collectionStats), originalLMWeight);
        mu2500Scorers.put(new ExpansionDocScorer(documentExpander, numExpansionDocs), 1 - originalLMWeight);
        docScorer = new InterpolatedDocScorer(mu2500Scorers);

        Map<DocScorer, Double> mu0Scorers = new HashMap<>();
        mu0Scorers.put(new DirichletDocScorer(0, collectionStats), originalLMWeight);
        mu0Scorers.put(new ExpansionDocScorer(documentExpander, numExpansionDocs), 1 - originalLMWeight);
        zeroMuDocScorer = new InterpolatedDocScorer(mu0Scorers);
    }

}
