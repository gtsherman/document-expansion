package org.retrievable.documentExpansion.main

import edu.gslis.searchhits.IndexBackedSearchHit
import edu.gslis.searchhits.SearchHits
import edu.gslis.textrepresentation.FeatureVector
import edu.gslis.utils.Stopper
import org.apache.commons.configuration.PropertiesConfiguration
import org.retrievable.documentExpansion.utils.OptimalParameters
import org.retrievable.document_expansion.expansion.DocumentExpander
import org.retrievable.document_expansion.features.LMFeatures
import org.retrievable.document_expansion.lms.LanguageModelEstimator
import org.retrievable.document_expansion.scoring.ExpansionDocScorer
import java.io.File
import java.nio.file.Paths


fun main(args: Array<String>) {
    // Load config
    val config = PropertiesConfiguration(args[0])

    // Load resources from config
    val switch = CollectionSwitch(config.getString("indexes-dir"), config.getString("queries-dir"), config.getString("qrels-dir"))
    val stopper = Stopper(config.getString("stoplist"))

    val hits = File(args[1]).readLines().map {
        val parts = it.split(",")
        AnnotatedDocument(parts[0], parts[1], parts[2])
    }

    val headers = arrayOf(
            "docno",
			"query",
			"originalToExpandedKL",
			"originalToExpansionKL",
			"originalToExpandedCosine",
			"originalToExpansionCosine",
			"perplexityOfOriginal",
			"pairwiseSimilarityCosine",
			"averageGroupSimilarityCosine"
    )

    //println(headers.joinToString(","))

    val features = hits.parallelStream().map { hit ->
        val query = switch.queries(hit.indexName).getNamedQuery(hit.queryTitle)

        // Get optimal expansion parameters for this query
        val collectionConfig = PropertiesConfiguration(File(Paths.get(config.getString("config-dir"), hit.indexName + ".wiki.properties").toString()))
        val expansionParams = OptimalParameters(File(collectionConfig.getString("optimal-params")), query.title)

        // Create document expander
        val documentExpander = DocumentExpander(switch.index("wikipedia"), expansionParams.numTerms, stopper)

        val document = IndexBackedSearchHit(switch.index(hit.indexName))
        document.docno = hit.docno

        // Get language models
        document.featureVector.applyStopper(stopper)
        document.featureVector.clip(20)
        val originalLM = LanguageModelEstimator.languageModel(document, switch.index(hit.indexName))

        val expansionLM = LanguageModelEstimator.expansionLanguageModel(
                document,
                ExpansionDocScorer(documentExpander, expansionParams.numDocs),
                originalLM.features
        )

        val expandedLM = LanguageModelEstimator.combinedLanguageModel(originalLM, expansionLM, expansionParams.origWeight)

        // Compute post-expansion features
        val originalToExpandedKL = LMFeatures.languageModelsKL(originalLM, expandedLM)
        val originalToExpansionKL = LMFeatures.languageModelsKL(originalLM, expansionLM)
        val perplexityOfOriginal = LMFeatures.perplexity(document.featureVector, expandedLM)
        /*val pairwiseSimilarityOfExpansionDocsShannonJenson = pairwiseSimilarity(
                documentExpander.expandDocument(document, expansionParams.numDocs),
                LMFeatures::languageModelsShannonJensen
        )
        val averageGroupSimilarityShannonJensen = averageGroupSimilarity(
                documentExpander.expandDocument(document, expansionParams.numDocs),
                LMFeatures::languageModelsShannonJensen
        )*/
        val originalToExpandedCosine = LMFeatures.languageModelsCosine(originalLM, expandedLM)
        val originalToExpansionCosine = LMFeatures.languageModelsCosine(originalLM, expansionLM)
        val pairwiseSimilarityOfExpansionDocsCosine = pairwiseSimilarity(
                documentExpander.expandDocument(document, expansionParams.numDocs),
                LMFeatures::languageModelsCosine
        )
        val averageGroupSimilarityCosine = averageGroupSimilarity(
                documentExpander.expandDocument(document, expansionParams.numDocs),
                LMFeatures::languageModelsCosine
        )


        document.docno + "," +
        query.title + "," +
        doubleArrayOf(
                originalToExpandedKL,
                originalToExpansionKL,
                originalToExpandedCosine,
                originalToExpansionCosine,
                perplexityOfOriginal,
                //pairwiseSimilarityOfExpansionDocsShannonJenson,
                pairwiseSimilarityOfExpansionDocsCosine,
                //averageGroupSimilarityShannonJensen,
                averageGroupSimilarityCosine
        ).joinToString(",")
    }

    println(features.toArray().joinToString("\n"))
}

fun pairwiseSimilarity(documents: SearchHits, similarityFunction: (FeatureVector, FeatureVector) -> Double) : Double {
    val similarities = ArrayList<Double>()
    for (i in 0 until documents.size()) {
        for (j in (i+1) until documents.size()) {
            val similarity = similarityFunction(documents.getHit(i).featureVector, documents.getHit(j).featureVector)
            similarities.add(similarity)
        }
    }
    return similarities.sum() / similarities.size
}

fun averageGroupSimilarity(documents: SearchHits, similarityFunction: (FeatureVector, FeatureVector) -> Double) : Double {
    val similarities: List<Double> = documents.map { document ->
        val otherDocsPseudoVector = FeatureVector(null)
        documents.filterNot { it === document }.forEach { otherDoc ->
            otherDoc.featureVector.forEach { term -> otherDocsPseudoVector.addTerm(term, otherDoc.featureVector.getFeatureWeight(term)) }
        }
        return similarityFunction(document.featureVector, otherDocsPseudoVector)
    }
    return similarities.sum() / similarities.size
}
