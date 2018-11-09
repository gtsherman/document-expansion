package org.retrievable.documentExpansion.features

import edu.gslis.docscoring.support.IndexBackedCollectionStats
import edu.gslis.indexes.IndexWrapper
import edu.gslis.queries.GQuery
import edu.gslis.scoring.DirichletDocScorer
import edu.gslis.scoring.expansion.StandardRM1Builder
import edu.gslis.scoring.queryscoring.QueryLikelihoodQueryScorer
import edu.gslis.searchhits.SearchHit
import edu.gslis.searchhits.SearchHits
import edu.gslis.utils.Stopper
import org.retrievable.documentExpansion.analysis.pseudoQueryTermRecall
import org.retrievable.document_expansion.expansion.DocumentExpander
import kotlin.math.log2


/**
 * Get document length
 *
 * @param document
 * @return The length of the document
 */
fun documentLength(document: SearchHit) : Double {
    return document.featureVector.length
}

/**
 * Compute document diversity. Diversity refers to percentage of terms in the document that are unique.
 *
 * @param document
 * @return The diversity percentage
 */
fun documentDiversity(document: SearchHit) : Double {
    return document.featureVector.featureCount / documentLength(document)
}

/**
 * Get the rank of a document in the search results list.
 *
 * @param document
 * @param results A search results list in SearchHits form
 * @return The rank of the document in the results list
 */
fun documentRank(document: SearchHit, results: SearchHits) : Int {
    return results.map { it.docno }.indexOf(document.docno) + 1
}

/**
 * Compute the simplified clarity of the document against the background collection. See Cronen-Townsend, Zhou, & Croft.
 *
 * @param document
 * @param index The index of the background collection
 * @return The clarity of the document against the background collection
 */
fun documentSCS(document: SearchHit, index: IndexWrapper) : Double {
    return document.featureVector.clarity(index)
}

/**
 * Compute the query prominence in the document. Query prominence is defined as the recall of the query terms in the
 * document pseudo-query. A prominence of 1 indicates that all query terms appear in the document pseudo-query.
 *
 * @param document
 * @param query
 * @param documentExpander The document expander must be pre-set with the number of terms to maintain in the pseudo-query
 */
fun queryProminence(document: SearchHit, query: GQuery, documentExpander: DocumentExpander) : Double {
    return pseudoQueryTermRecall(query.featureVector.map { it }, document, documentExpander)
}

/**
 * Compute the change in the document's query likelihood score for an RM1 as compared to a standard Dirichlet smoothed
 * QL run.
 *
 * @param document
 * @param query The original query
 * @param rm1 The RM1 represented as a query
 * @param scorer The query scorer to use in computing both QL scores
 * @return The difference between the two scores, i.e. scoreRM1(d, q) - scoreBaseline(d, q)
 */
fun rmImprovementQL(document: SearchHit, query: GQuery, rm1: GQuery, scorer: QueryLikelihoodQueryScorer) : Double {
    return scorer.scoreQuery(rm1, document) - scorer.scoreQuery(query, document)
}

/**
 * Compute the change in the document's rank for an RM1 as compared to a standard Dirichlet smoothed QL run.
 *
 * @param document
 * @param originalResults The original search results list under the baseline QL run
 * @param rmResults The search results list under the RM1 run
 * @return The rank difference between the two scores, i.e. rankRM1(d, q) - rankBaseline(d, q) @param
 */
fun rmImprovementRank(document: SearchHit, originalResults: SearchHits, rmResults: SearchHits) : Int {
    val rmRank = documentRank(document, rmResults)
    val origRank = documentRank(document, originalResults)

    // Covers no rank change or doesn't appear in both (i.e. both rank == 0, which shouldn't really happen)
    return if (rmRank == origRank) {
        -1
    } else if (rmRank < origRank || origRank == 0) {
        1
    } else { // (rmRank > origRank || rmRank == 0)
        -1
    }
}

/**
 * Compute the information theoretic entropy of the document in bits.
 *
 * Entropy measures the average information content of a probability distribution. It is computed as follows:
 *  H(X) = -1 * Sum_x [ P(x)*log2[P(x)] ]
 *
 * This function computes entropy over the unsmoothed term distribution of a document.
 *
 * @param document
 * @return The entropy of the document as computed over terms
 */
fun documentEntropy(document: SearchHit) : Double {
    return -1 * document.featureVector.map {
        val termProb = document.featureVector.getFeatureWeight(it) / document.featureVector.length
        termProb * log2(termProb)
    }.sum()
}

private fun rmQuery(query: GQuery, index: IndexWrapper, stopper: Stopper) : GQuery {
    val collectionStats = IndexBackedCollectionStats()
    collectionStats.setStatSource(index)
    val rm = StandardRM1Builder(collectionStats)
            .buildRelevanceModel(query, index.runQuery(query, StandardRM1Builder.DEFAULT_FEEDBACK_DOCS), stopper)
    val rmQuery = GQuery()
    rmQuery.featureVector = rm
    return rmQuery
}

fun documentRMRankChange(document: SearchHit, query: GQuery, index: IndexWrapper, stopper: Stopper) : Int {
    val rmQuery = rmQuery(query, index, stopper)
    return documentRank(document, index.runQuery(rmQuery, 100))
}

fun documentRMScoreChange(document: SearchHit, query: GQuery, index: IndexWrapper, stopper: Stopper) : Double {
    val rmQuery = rmQuery(query, index, stopper)
    val collectionStats = IndexBackedCollectionStats()
    collectionStats.setStatSource(index)
    return QueryLikelihoodQueryScorer(DirichletDocScorer(collectionStats)).scoreQuery(rmQuery, document)
}
