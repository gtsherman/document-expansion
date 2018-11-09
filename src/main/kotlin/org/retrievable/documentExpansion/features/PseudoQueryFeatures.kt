package org.retrievable.documentExpansion.features

import edu.gslis.docscoring.support.IndexBackedCollectionStats
import edu.gslis.indexes.IndexWrapper
import edu.gslis.queries.GQuery
import edu.gslis.scoring.DirichletDocScorer
import edu.gslis.scoring.queryscoring.QueryLikelihoodQueryScorer
import edu.gslis.searchhits.SearchHit
import edu.gslis.textrepresentation.FeatureVector
import edu.gslis.utils.Stopper
import org.retrievable.document_expansion.expansion.DocumentExpander


/**
 * Compute the total probability mass contributed to the document language model by the specified pseudo-query terms.
 *
 * @param document
 * @param pseudoQuery
 * @return The sum of the pseudo-query term probabilities.
 */
fun probabilityFraction(document: SearchHit, pseudoQuery: GQuery) : Double {
    return pseudoQuery.featureVector.map { document.featureVector.getFeatureWeight(it) / document.featureVector.length }
            .sum()
}

/**
 * Compute the rank of the document when its pseudo-query is issued as a pseudoQuery against the document's collection.
 *
 * @param document
 * @param pseudoQuery
 * @param index The index of the document's source collection.
 * @return The rank of the document in the pseudo-query results.
 */
fun documentSelfRetrievalRank(document: SearchHit, pseudoQuery: GQuery, index: IndexWrapper) : Int {
    return documentRank(document, index.runQuery(pseudoQuery, 1000))
}

/**
 * Compute the retrieval score of the document when its pseudo-query is issued against the document's collection.
 *
 * @param document
 * @param pseudoQuery
 * @param index The index of the document's source collection.
 * @return The retrieval score of the document in the pseudo-query results.
 */
fun documentSelfRetrievalScore(document: SearchHit, pseudoQuery: GQuery, index: IndexWrapper) : Double {
    val collectionStats = IndexBackedCollectionStats()
    collectionStats.setStatSource(index)
    return QueryLikelihoodQueryScorer(DirichletDocScorer(collectionStats)).scoreQuery(pseudoQuery, document)
}

fun expansionPseudoQuery(document: SearchHit, documentExpander: DocumentExpander, stopper: Stopper) : GQuery {
    val docPseudoQuery = GQuery()

    val docVector = FeatureVector(null)
    documentExpander.expandDocument(document).forEach {
        searchHit -> searchHit.featureVector.forEach { term ->
            val termWeight = searchHit.featureVector.getFeatureWeight(term) / searchHit.featureVector.length * searchHit.score
            docVector.addTerm(term, termWeight)
        }
    }

    docPseudoQuery.featureVector = docVector

    if (stopper != null) {
        docPseudoQuery.applyStopper(stopper)
    }
    docPseudoQuery.featureVector.clip(20)

    return docPseudoQuery
}
