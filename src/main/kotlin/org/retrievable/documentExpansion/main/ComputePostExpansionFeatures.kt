package org.retrievable.documentExpansion.main

import edu.gslis.eval.Qrels
import edu.gslis.indexes.IndexWrapperIndriImpl
import edu.gslis.queries.GQueriesFactory
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
import java.util.*


fun main(args: Array<String>) {
    // Load config
    val config = PropertiesConfiguration(args[0])
    //val docListFile = args[1]

    // Load resources from config
    val stopper = Stopper(config.getString("stoplist"))
    val targetIndex = IndexWrapperIndriImpl(config.getString("target-index"))
    val expansionIndex = IndexWrapperIndriImpl(config.getString("expansion-index"))
    val queries = GQueriesFactory.getGQueries(config.getString("queries"))
    val qrels = Qrels(config.getString("qrels"), false, 1)

    /*val hits = File(docListFile).readLines().map {
        val parts = it.split(",")
        AnnotatedDocument(parts[0], parts[1])
    }*/

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

    val scanner = Scanner(System.`in`)
    while (scanner.hasNextLine()) {
        val parts = scanner.nextLine().split(",")
        val hit = AnnotatedDocument(parts[0], parts[1])

        val query = queries.getNamedQuery(hit.queryTitle)

        // Get optimal expansion parameters for this query
        val expansionParams = OptimalParameters(File(config.getString("optimal-params")), query.title)

        // Create document expander
        val documentExpander = DocumentExpander(expansionIndex, expansionParams.numTerms, stopper)

        val document = IndexBackedSearchHit(targetIndex)
        document.docno = hit.docno

        // Get language models
        document.featureVector.applyStopper(stopper)
        document.featureVector.clip(20)
        val originalLM = LanguageModelEstimator.languageModel(document, targetIndex)

        val expansionLM = LanguageModelEstimator.expansionLanguageModel(
                document,
                ExpansionDocScorer(documentExpander, expansionParams.numDocs),
                originalLM.features
        )

        val expandedLM = LanguageModelEstimator.combinedLanguageModel(originalLM, expansionLM, expansionParams.origWeight)

        // Compute post-expansion features
        //val originalToExpandedKL = LMFeatures.languageModelsKL(originalLM, expandedLM)
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
        //val originalToExpandedCosine = LMFeatures.languageModelsCosine(originalLM, expandedLM)
        val originalToExpansionCosine = LMFeatures.languageModelsCosine(originalLM, expansionLM)
        val pairwiseSimilarityOfExpansionDocsCosine = pairwiseSimilarity(
                documentExpander.expandDocument(document, expansionParams.numDocs),
                LMFeatures::languageModelsCosine
        )
        val averageGroupSimilarityCosine = averageGroupSimilarity(
                documentExpander.expandDocument(document, expansionParams.numDocs),
                LMFeatures::languageModelsCosine
        )


        println(document.docno + "," +
        query.title + "," +
        doubleArrayOf(
                //originalToExpandedKL,
                originalToExpansionKL,
                //originalToExpandedCosine,
                originalToExpansionCosine,
                perplexityOfOriginal,
                //pairwiseSimilarityOfExpansionDocsShannonJenson,
                pairwiseSimilarityOfExpansionDocsCosine,
                //averageGroupSimilarityShannonJensen,
                averageGroupSimilarityCosine
        ).joinToString(","))
    }
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
