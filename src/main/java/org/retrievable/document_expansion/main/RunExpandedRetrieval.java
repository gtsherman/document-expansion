package org.retrievable.document_expansion.main;

import edu.gslis.docscoring.support.CollectionStats;
import edu.gslis.docscoring.support.IndexBackedCollectionStats;
import edu.gslis.indexes.CachedFeatureVectorIndexWrapperIndriImpl;
import edu.gslis.indexes.IndexWrapperIndriImpl;
import edu.gslis.output.FormattedOutputTrecEval;
import edu.gslis.queries.GQueries;
import edu.gslis.queries.GQueriesFactory;
import edu.gslis.queries.GQuery;
import edu.gslis.scoring.CachedDocScorer;
import edu.gslis.scoring.DirichletDocScorer;
import edu.gslis.scoring.DocScorer;
import edu.gslis.scoring.InterpolatedDocScorer;
import edu.gslis.scoring.queryscoring.QueryLikelihoodQueryScorer;
import edu.gslis.scoring.queryscoring.QueryScorer;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.utils.Stopper;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.retrievable.document_expansion.expansion.DocumentExpander;
import org.retrievable.document_expansion.scoring.ExpansionDocScorer;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * New model:
 * Run this code
 */
public class RunExpandedRetrieval {

    public static void main(String[] args) throws ConfigurationException {
        // Load configuration
        Configuration config = new PropertiesConfiguration(args[0]);

        // Load run parameters
        int numTerms = Integer.parseInt(args[1]);
        String queryName = args[2];

        // Load resources
        Stopper stopper = new Stopper(config.getString("stoplist"));

        IndexWrapperIndriImpl targetIndex = new IndexWrapperIndriImpl(config.getString("target-index"));
        IndexWrapperIndriImpl expansionIndex = new CachedFeatureVectorIndexWrapperIndriImpl(config.getString("expansion-index"));

        GQueries queries = GQueriesFactory.getGQueries(config.getString("queries"));
        GQuery query = queries.getNamedQuery(queryName);

        CollectionStats targetCollectionStats = new IndexBackedCollectionStats();
        targetCollectionStats.setStatSource(config.getString("target-index"));

        int minNumDocs = Integer.parseInt(config.getString("min-docs", "5"));
        int maxNumDocs = Integer.parseInt(config.getString("max-docs", "25"));
        int numDocsInterval = Integer.parseInt(config.getString("docs-interval", "5"));

        DocumentExpander docExpander = new DocumentExpander(expansionIndex, numTerms, stopper);
        docExpander.setMaxNumDocs(maxNumDocs);

        FormattedOutputTrecEval out = FormattedOutputTrecEval.getInstance(
                "tmp",
                new BufferedWriter(new OutputStreamWriter(System.out))
        );

        // Create scorers
        DocScorer dirichletScorer = new CachedDocScorer(new DirichletDocScorer(targetCollectionStats));
        ExpansionDocScorer expansionScorer = new ExpansionDocScorer(2500, docExpander);

        Map<DocScorer, Double> scorers = new HashMap<>();
        DocScorer interpolatedScorer = new InterpolatedDocScorer(scorers);
        QueryScorer queryScorer = new QueryLikelihoodQueryScorer(interpolatedScorer);

        // Get initial results
        query.applyStopper(stopper);
        SearchHits results = targetIndex.runQuery(query, 1000);

        // Iterate over parameter settings
        for (int numDocs = minNumDocs; numDocs <= maxNumDocs; numDocs += numDocsInterval) {
            expansionScorer.setNumDocs(numDocs);
            for (int origWeightInt = 0; origWeightInt <= 10; origWeightInt++) {
                double origWeight = origWeightInt / 10.0;

                scorers.put(dirichletScorer, origWeight);
                scorers.put(expansionScorer, 1 - origWeight);

                out.setRunId("origW:" + origWeight + ",expDocs:" + numDocs + ",expTerms:" + numTerms);

                for (SearchHit doc : results) {
                    double expandedScore = queryScorer.scoreQuery(query, doc);
                    doc.setScore(expandedScore);
                }

                results.rank();
                out.write(results, query.getTitle());
            }
        }

        out.close();
    }

}
