package org.retrievable.documentExpansion.features

import edu.gslis.searchhits.SearchHit
import edu.gslis.searchhits.SearchHits
import edu.gslis.textrepresentation.FeatureVector

import org.retrievable.document_expansion.features.LMFeatures


fun pairwiseSimilarity(documents: SearchHits,
                       similarityFunction: (FeatureVector, FeatureVector) -> Double = LMFeatures::languageModelsShannonJensen): Double {
    // Convert documents to set
    val docSet: Set<SearchHit> = documents.hits().toSet()

    val scores = ArrayList<Double>()

    docSet.forEach { document ->
        docSet.minus(document).forEach { otherDocument ->
            scores.add(similarityFunction(document.featureVector, otherDocument.featureVector))
        }
    }

    return scores.average()
}