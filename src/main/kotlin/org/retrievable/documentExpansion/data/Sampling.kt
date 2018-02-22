package org.retrievable.documentExpansion.data

import edu.gslis.searchhits.SearchHit
import edu.gslis.searchhits.SearchHits
import edu.gslis.textrepresentation.FeatureVector
import edu.gslis.utils.Stopper
import org.apache.lucene.analysis.snowball.SnowballFilter
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.tartarus.snowball.ext.EnglishStemmer
import java.lang.Math.min
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.HashSet


fun weightedSample(weightedItems: Map<String, Double>) : String {
    if (weightedItems.isEmpty()) {
        return ""
    }

    // The random number
    val random = Random().nextDouble()

    // Ensure that the weights sum to 1
    val totalWeight = weightedItems.values.sum()
    val normalizedWeightedItems = HashMap<String, Double>()
    weightedItems.keys.forEach { key -> normalizedWeightedItems[key] = weightedItems[key] ?: 0.0 / totalWeight }

    // When the sum of the weights is greater than or equal to the random number, return that item
    val items = weightedItems.keys.iterator()
    var item = items.next()
    var sum = weightedItems[item] ?: 0.0
    while (sum < random && items.hasNext()) {
        item = items.next()
        sum += weightedItems[item] ?: 0.0
    }
    return item
}

fun sampleDocuments(number: Int, documents: SearchHits) : Set<SearchHit> {
    val sample = HashSet<SearchHit>()
    repeat(
            min(number, documents.size()),
            {
                val randomInt = Random().nextInt(documents.size())
                sample.add(documents.getHit(randomInt))
                documents.remove(randomInt)
            }
    )
    return sample
}

fun sampleTerms(number: Int, termVector: FeatureVector, stopper: Stopper = Stopper(), stem: Boolean = false, exclude: Set<String> = HashSet()) : Set<String> {
    /*val docVector = document.featureVector.deepCopy()
    docVector.applyStopper(stopper)
    docVector.clip(number)

    return if (stem) stemText(docVector.features.joinToString(" ")) else docVector.features*/
    val vector = termVector.deepCopy()
    vector.applyStopper(stopper)

    val selectedTerms = HashSet<String>()

    var weightedTerms = vector.features.filterNot { term -> exclude.contains(term) }.associate { term -> Pair<String, Double>(term, vector.getFeatureWeight(term)) }.toMap()
    repeat(min(number, weightedTerms.size), {
        val sampledTerm = weightedSample(weightedTerms)
        selectedTerms.add(sampledTerm)
        weightedTerms = weightedTerms.minus(sampledTerm)
    })

    return if (stem) stemText(selectedTerms.joinToString(" ")) else selectedTerms
}

fun stemText(text: String) : Set<String> {
    val tokens = HashSet<String>()

    val tokenStream = SnowballFilter(StandardAnalyzer().tokenStream(null, text), EnglishStemmer())
    //val tokenStream = EnglishMinimalStemFilter(StandardAnalyzer().tokenStream(null, docVector.features.joinToString(" ")))
    val characterRepresentation = tokenStream.addAttribute(CharTermAttribute::class.java)
    tokenStream.reset()

    while (tokenStream.incrementToken()) {
        tokens.add(characterRepresentation.toString())
    }

    return tokens
}