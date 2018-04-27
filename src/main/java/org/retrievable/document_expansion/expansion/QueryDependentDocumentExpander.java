package org.retrievable.document_expansion.expansion;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import edu.gslis.indexes.IndexWrapper;
import edu.gslis.indexes.IndexWrapperIndriImpl;
import edu.gslis.queries.GQuery;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.textrepresentation.FeatureVector;
import edu.gslis.utils.Stopper;
import kotlin.Pair;
import lemurproject.indri.QueryEnvironment;
import org.apache.commons.lang3.StringUtils;

import java.util.concurrent.ExecutionException;

public class QueryDependentDocumentExpander extends DocumentExpander {

    private GQuery query;
    private double queryWeight;

    private LoadingCache<Pair<SearchHit, Double>, SearchHits> expandedDocs = CacheBuilder.newBuilder()
            .softValues()
            .build(
                    new CacheLoader<Pair<SearchHit, Double>, SearchHits>() {
                        public SearchHits load(Pair<SearchHit, Double> documentAndQueryWeight) {
                            SearchHit document = documentAndQueryWeight.getFirst();
                            double queryWeight = documentAndQueryWeight.getSecond();
                            setQueryWeight(queryWeight);
                            return expandDocumentByRetrieval(document, maxNumDocs);
                        }
                    });

    public QueryDependentDocumentExpander(IndexWrapperIndriImpl index) {
        super(index);
    }

    public QueryDependentDocumentExpander(IndexWrapperIndriImpl index, Stopper stopper) {
        super(index, stopper);
    }

    public QueryDependentDocumentExpander(IndexWrapperIndriImpl index, int numTerms, Stopper stopper) {
        super(index, numTerms, stopper);
    }

    public GQuery getQuery() {
        return this.query;
    }

    public void setQuery(GQuery query) {
        this.query = query;
    }

    public double getQueryWeight() {
        return queryWeight;
    }

    public void setQueryWeight(double queryWeight) {
        this.queryWeight = queryWeight;
    }

    public SearchHits expandDocument(SearchHit document, int numDocs) {
        if (numDocs > maxNumDocs) {
            setMaxNumDocs(numDocs);
        }

        try {
            SearchHits expansionDocuments = expandedDocs.get(new Pair<>(document, getQueryWeight()));
            return croppedHits(expansionDocuments, numDocs);
        } catch (ExecutionException e) {
            System.err.println("Error getting expanded document " + document.getDocno() + " from the cache.");
            e.printStackTrace(System.err);
            return new SearchHits();
        }
    }

    @Override
    public GQuery createDocumentPseudoQuery(SearchHit document) {
        // Make a copy of query vector to prevent side effects from stopping
        FeatureVector queryVector = query.getFeatureVector().deepCopy();
        queryVector.applyStopper(stopper);
        queryVector.normalize();

        // Build the standard query-independent pseudo-query
        GQuery standardPseudoQuery = super.createDocumentPseudoQuery(document);
        standardPseudoQuery.getFeatureVector().normalize();

        // Combine the two vectors. Probably need a more nuanced way of interpolating.
        FeatureVector combinedPseudoQueryVector = FeatureVector.interpolate(
                queryVector, standardPseudoQuery.getFeatureVector(), queryWeight
        );

        // Convert to GQuery
        GQuery queryDependentPseudoQuery = new GQuery();
        queryDependentPseudoQuery.setFeatureVector(combinedPseudoQueryVector);

        return queryDependentPseudoQuery;
    }
}
