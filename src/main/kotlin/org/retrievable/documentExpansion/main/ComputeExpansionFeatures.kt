package org.retrievable.documentExpansion.main

import edu.gslis.eval.Qrels
import edu.gslis.indexes.IndexWrapper
import edu.gslis.indexes.IndexWrapperIndriImpl
import edu.gslis.queries.GQueriesFactory
import edu.gslis.searchhits.IndexBackedSearchHit
import edu.gslis.searchhits.readTrecOutput
import edu.gslis.utils.Stopper
import org.apache.commons.configuration.PropertiesConfiguration
import org.retrievable.documentExpansion.features.documentSCS
import org.retrievable.documentExpansion.features.documentDiversity
import org.retrievable.documentExpansion.features.documentLength
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
    val targetIndex: IndexWrapper = IndexWrapperIndriImpl(config.getString("target-index"))
    val expansionIndex = IndexWrapperIndriImpl(config.getString("expansion-index"))
    val queries = GQueriesFactory.getGQueries(config.getString("queries"))
    val stopper = Stopper(config.getString("stoplist"))
    val qrels = Qrels(config.getString("qrels"), true, 1)
    val baselineResults = readTrecOutput(config.getString("baseline-run"))
    val expansionResults = readTrecOutput(config.getString("expansion-run"))

    println("docno,query,relevance,rankImprovement,initialRank,length,diversity,clarity,originalToExpandedKL,originalToExpansionKL,originalToExpandedCosine,originalToExpansionCosine,perplexityOfOriginal,pairwiseSimilarityCosine,averageGroupSimilarityCosine")

    for (query in queries) {
        // Make hits a set of documents a) retrieved for baseline, b) retrieved for expansion, c) in judgment pool
        val baselineHits = baselineResults.getSearchHits(query)?.hits()?.map { it.docno } ?: listOf()
        //val retrievedHits = baselineHits.intersect(expansionResults.getSearchHits(query).hits().map { it.docno })
        //val hits = retrievedHits.intersect(qrels.getPool(query.title))
        val hits = baselineHits

        // For each retrieved document, compute:
        hits.forEach { doc ->
            val document = IndexBackedSearchHit(targetIndex)
            document.docno = doc

            // Compute pre-expansion features
            val length = documentLength(document)
            val diversity = documentDiversity(document)
            val initialRank = documentRank(document, baselineResults.getSearchHits(query))
            val clarity = documentSCS(document, targetIndex)

            // Compute rank change
            val expansionRank = documentRank(document, expansionResults.getSearchHits(query))
            val rankChange = initialRank - expansionRank

            // Get optimal expansion parameters for this query
            val expansionParams = OptimalParameters(File(config.getString("optimal-params")), query.title)

            // Create document expander
            val documentExpander = DocumentExpander(expansionIndex, expansionParams.numTerms, stopper)

            // Get language models
            document.featureVector.applyStopper(stopper)
            document.featureVector.clip(10)
            val originalLM = LanguageModelEstimator.languageModel(document, targetIndex)

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

            // Print data// Print data
            println(
                    document.docno + "," +
                            query.title + "," +
                            doubleArrayOf(
                                    qrels.getRelLevel(query.title, document.docno).toDouble(),
                                    rankChange.toDouble(),
                                    initialRank.toDouble(),
                                    length,
                                    diversity,
                                    clarity,
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