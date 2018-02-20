package org.retrievable.documentExpansion.features

import edu.gslis.indexes.IndexWrapper
import edu.gslis.searchhits.SearchHit
import edu.gslis.searchhits.SearchHits


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
    return results.map({ it.docno }).indexOf(document.docno) + 1
}

/**
 * Compute the clarity of the document against the background collection. See Cronen-Townsend, Zhou, & Croft.
 *
 * @param document
 * @param index The index of the background collection
 * @return The clarity of the document against the background collection
 */
fun documentClarity(document: SearchHit, index: IndexWrapper) : Double {
    return document.featureVector.clarity(index)
}
