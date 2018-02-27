package org.retrievable.document_expansion.expansion;

import edu.gslis.indexes.IndexWrapper;
import edu.gslis.indexes.IndexWrapperIndriImpl;
import edu.gslis.queries.GQuery;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.textrepresentation.FeatureVector;
import edu.gslis.utils.Stopper;
import lemurproject.indri.QueryEnvironment;
import org.apache.commons.lang3.StringUtils;

public class QueryDependentDocumentExpander extends DocumentExpander {

    private GQuery query;

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

    @Override
    public GQuery createDocumentPseudoQuery(SearchHit document) {
        if (query == null) {
            System.err.println("There is no query set. Returning a frequency-based pseudo-query.");
            return super.createDocumentPseudoQuery(document);
        }

        GQuery docPseudoQuery = new GQuery();
        docPseudoQuery.setFeatureVector(new FeatureVector(null));

        FeatureVector documentTerms = document.getFeatureVector().deepCopy();
        FeatureVector queryTerms = query.getFeatureVector().deepCopy();

        if (stopper != null) {
            documentTerms.applyStopper(stopper);
            queryTerms.applyStopper(stopper);
        }

        for (String term : documentTerms.getFeatures()) {
            String termWithQuery = StringUtils.join(queryTerms.getFeatures(), " ") + " " + term;
            QueryEnvironment queryEnvironment = (QueryEnvironment) index.getActualIndex();

            double coocurrenceDocs = 0;
            double termDocs = index.docFreq(term);

            if (termDocs == 0) { // this happens if the term appears in the target document but not the expansion index
                continue;
            }

            try {
                coocurrenceDocs = queryEnvironment.expressionCount("#uw( " + termWithQuery + " )");
            } catch (Exception e) {
                System.err.println("Error getting document expression count.");
                e.printStackTrace(System.err);
            }

            docPseudoQuery.getFeatureVector().setTerm(term, coocurrenceDocs / termDocs);
        }

        docPseudoQuery.getFeatureVector().clip(numTerms);

        return docPseudoQuery;
    }
}
