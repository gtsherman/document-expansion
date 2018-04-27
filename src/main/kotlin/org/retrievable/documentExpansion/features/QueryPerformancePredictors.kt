package org.retrievable.documentExpansion.features

import edu.gslis.indexes.IndexWrapper
import edu.gslis.queries.GQuery
import org.apache.commons.math3.stat.descriptive.moment.Variance
import kotlin.math.exp
import kotlin.math.ln

fun idf(term: String, index: IndexWrapper): Double {
    val N = index.docCount()
    val n = index.docFreq(term)
    return Math.log((N - n + 0.5) / (n + 0.5))
}

fun avgIDF(query: GQuery, index: IndexWrapper) : Double {
    return query.featureVector.map { term -> idf(term, index) }.average()
}

fun avgICTF(query: GQuery, index: IndexWrapper) : Double {
    return query.featureVector.map { ln(index.termCount() / index.termFreq(it)) }.average()
}

/**
 * Compute the simplified clarity score. See He and Ounis "Inferring query performance using pre-retrieval predictors"
 * (2004).
 *
 * The simplified clarity score is simply the KL divergence of the query to the collection. It is simplified in that it
 * does not require computing a relevance model.
 */
fun scs(query: GQuery, index: IndexWrapper) : Double {
    return query.featureVector.clarity(index)
}

/**
 * Compute the collection query similarity of each query term. See Zhao et al. "Effective pre-retrieval query
 * performance prediction using similarity and variable evidence" (2008).
 *
 * The scores returned by this function most likely need to be summarized. Zhao et al. test selecting the average, max,
 * and sum.
 *
 * @return A list of collection query similarity scores, one for each term in the query.
 */
fun scq(query: GQuery, index: IndexWrapper) : List<Double> {
    return query.featureVector.map { (1 + ln(index.termFreq(it))) * idf(it, index) }
}

/**
 * Compute the variance of term weights across documents. See Zhao et al. "Effective pre-retrieval query performance
 * prediction using similarity and variable evidence" (2008).
 *
 * The scores returned by this function most likely need to be summarized. Zhao et al. test selecting the average, max,
 * and sum.
 *
 * @return A list of term weight variances, one for each term in the query.
 */
fun termVar(query: GQuery, index: IndexWrapper) : List<Double> {
    return query.featureVector.map { term ->
        // Limit to 1000 for performance reasons, but theoretically should be computed over all docs containing the term
        val docs = index.runQuery(term, 1000)

        // Compute the term weight in each document
        val docWeights = docs.map { doc -> exp(doc.score) }.toDoubleArray()

        // Compute the variance of the term weights
        Variance().evaluate(docWeights)
    }
}