package org.retrievable.documentExpansion.main

import edu.gslis.eval.Qrels
import edu.gslis.indexes.IndexWrapperIndriImpl
import edu.gslis.queries.GQueriesFactory
import edu.gslis.queries.GQuery
import edu.gslis.searchhits.IndexBackedSearchHit
import edu.gslis.searchhits.SearchHits
import edu.gslis.searchhits.readTrecOutput
import edu.gslis.textrepresentation.FeatureVector
import edu.gslis.utils.Stopper
import org.apache.commons.configuration.PropertiesConfiguration
import org.retrievable.documentExpansion.features.documentRank
import org.retrievable.documentExpansion.utils.OptimalParameters
import org.retrievable.document_expansion.expansion.DocumentExpander
import org.retrievable.document_expansion.features.LMFeatures
import org.retrievable.document_expansion.lms.LanguageModelEstimator
import org.retrievable.document_expansion.scoring.ExpansionDocScorer
import java.io.File


fun main(args: Array<String>) {
    // Load config
    val config = PropertiesConfiguration(args[0])

    // Load resources from config
    val targetIndex = IndexWrapperIndriImpl(config.getString("target-index"))
    val expansionIndex = IndexWrapperIndriImpl(config.getString("expansion-index"))
    val queries = GQueriesFactory.getGQueries(config.getString("queries"))
    val stopper = Stopper(config.getString("stoplist"))
    val qrels = Qrels(config.getString("qrels"), true, 1)
    val baselineResults = readTrecOutput(config.getString("baseline-run"))
    val expansionResults = readTrecOutput(config.getString("expansion-run"))

    println("docno,query,relevance,rankImprovement,originalToExpandedKL,originalToExpansionKL,originalToExpandedCosine,originalToExpansionCosine,perplexityOfOriginal,pairwiseSimilarityCosine,averageGroupSimilarityCosine")

    queries.forEach { query: GQuery ->
        // Get optimal expansion parameters for this query
        val expansionParams = OptimalParameters(File(config.getString("optimal-params")), query.title)

        // Create document expander
        val documentExpander = DocumentExpander(expansionIndex, expansionParams.numTerms, stopper)

        // Make hits a set of documents a) retrieved for baseline, b) retrieved for expansion, c) in judgment pool
        val baselineHits = baselineResults.getSearchHits(query).hits().map { it.docno }
        val retrievedHits = baselineHits.intersect(expansionResults.getSearchHits(query).hits().map { it.docno })
        val judgedRetrievedHits = retrievedHits.intersect(qrels.getPool(query.title))

        // For each retrieved document, compute:
        judgedRetrievedHits.forEach { doc ->
            val document = IndexBackedSearchHit(targetIndex)
            document.docno = doc

            // Get language models
            val times = ArrayList<Long>()
            times.add(System.currentTimeMillis()) // 0
            document.featureVector.applyStopper(stopper)
            document.featureVector.clip(10)
            val originalLM = LanguageModelEstimator.languageModel(document, targetIndex)

            times.add(System.currentTimeMillis()) // 1
            val expansionLM = LanguageModelEstimator.expansionLanguageModel(
                    document,
                    ExpansionDocScorer(documentExpander, expansionParams.numDocs),
                    originalLM.features
            )

            times.add(System.currentTimeMillis()) // 2
            val expandedLM = LanguageModelEstimator.combinedLanguageModel(originalLM, expansionLM, expansionParams.origWeight)

            // Compute post-expansion features
            times.add(System.currentTimeMillis()) // 3
            val originalToExpandedKL = LMFeatures.languageModelsKL(originalLM, expandedLM)
            times.add(System.currentTimeMillis()) // 4
            val originalToExpansionKL = LMFeatures.languageModelsKL(originalLM, expansionLM)
            times.add(System.currentTimeMillis()) // 5
            val perplexityOfOriginal = LMFeatures.perplexity(document.featureVector, expandedLM)
            times.add(System.currentTimeMillis()) // 6
            /*val pairwiseSimilarityOfExpansionDocsShannonJenson = pairwiseSimilarity(
                    documentExpander.expandDocument(document, expansionParams.numDocs),
                    LMFeatures::languageModelsShannonJensen
            )
            times.add(System.currentTimeMillis()) // 7
            val averageGroupSimilarityShannonJensen = averageGroupSimilarity(
                    documentExpander.expandDocument(document, expansionParams.numDocs),
                    LMFeatures::languageModelsShannonJensen
            )*/
            times.add(System.currentTimeMillis()) // 8
            val originalToExpandedCosine = LMFeatures.languageModelsCosine(originalLM, expandedLM)
            times.add(System.currentTimeMillis()) // 9
            val originalToExpansionCosine = LMFeatures.languageModelsCosine(originalLM, expansionLM)
            times.add(System.currentTimeMillis()) // 10
            val pairwiseSimilarityOfExpansionDocsCosine = pairwiseSimilarity(
                    documentExpander.expandDocument(document, expansionParams.numDocs),
                    LMFeatures::languageModelsCosine
            )
            times.add(System.currentTimeMillis()) // 11
            val averageGroupSimilarityCosine = averageGroupSimilarity(
                    documentExpander.expandDocument(document, expansionParams.numDocs),
                    LMFeatures::languageModelsCosine
            )
            times.add(System.currentTimeMillis())

            System.err.println("Times:")
            for (i in 0 until times.size-1) {
                System.err.println("Block $i: ${times[i+1] - times[i]}")
            }

            // Compute rank change
            val initialRank = documentRank(document, baselineResults.getSearchHits(query))
            val expansionRank = documentRank(document, expansionResults.getSearchHits(query))
            val rankChange = initialRank - expansionRank

            // Print data
            println(
                    document.docno + "," +
                    query.title + "," +
                    doubleArrayOf(
                            qrels.getRelLevel(query.title, document.docno).toDouble(),
                            rankChange.toDouble(),
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
            )
        }
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
