package edu.gslis.scoring.expansion;

import edu.gslis.docscoring.support.CollectionStats;
import edu.gslis.scoring.DirichletDocScorer;
import edu.gslis.scoring.DocScorer;
import edu.gslis.scoring.InterpolatedDocScorer;
import org.retrievable.document_expansion.expansion.DocumentExpander;
import org.retrievable.document_expansion.scoring.ExpansionDocScorer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExpandedRM1Builder extends StandardRM1Builder {

    private int numExpansionDocs;
    private List<DocumentExpander> documentExpanders;
    private List<Double> interpolationWeights; // when null (default), standard RM1

    public ExpandedRM1Builder(CollectionStats collectionStats, List<DocumentExpander> documentExpanders, int numExpansionDocs) {
        this(StandardRM1Builder.DEFAULT_FEEDBACK_DOCS, StandardRM1Builder.DEFAULT_FEEDBACK_TERMS, collectionStats, documentExpanders, numExpansionDocs);
    }

    public ExpandedRM1Builder(int feedbackDocs, int feedbackTerms, CollectionStats collectionStats, List<DocumentExpander> documentExpanders, int numExpansionDocs) {
        super(feedbackDocs, feedbackTerms, collectionStats);
        this.documentExpanders = documentExpanders;
        this.numExpansionDocs = numExpansionDocs;
    }

    public void setInterpolationWeights(List<Double> interpolationWeights) {
        // Check whether we're actually changing the values of the weigths and only recreate the doc scorers if so
        if (this.interpolationWeights != interpolationWeights) {
            this.interpolationWeights = interpolationWeights;
            createDocScorers();
        }
    }

    @Override
    protected void createDocScorers() {
        if (interpolationWeights == null) {
            // If we haven't set interpolation weights, this defaults to a standard RM1 (which the
            // super.createDocScorers method will create).
            super.createDocScorers();
        } else {
            double originalLMWeight = interpolationWeights.get(0);

            Map<DocScorer, Double> mu2500Scorers = new HashMap<>();
            mu2500Scorers.put(new DirichletDocScorer(collectionStats), originalLMWeight);

            Map<DocScorer, Double> mu0Scorers = new HashMap<>();
            mu0Scorers.put(new DirichletDocScorer(0, collectionStats), originalLMWeight);

            for (int i = 1; i < interpolationWeights.size(); i++) {
                double weight = interpolationWeights.get(i);
                mu2500Scorers.put(new ExpansionDocScorer(documentExpanders.get(i - 1), numExpansionDocs), weight);
                mu0Scorers.put(new ExpansionDocScorer(documentExpanders.get(i - 1), numExpansionDocs), weight);
            }

            docScorer = new InterpolatedDocScorer(mu2500Scorers);
            zeroMuDocScorer = new InterpolatedDocScorer(mu0Scorers);
        }
    }

}
