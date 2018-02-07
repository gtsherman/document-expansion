package org.retrievable.documentExpansion.main

import edu.gslis.docscoring.support.IndexBackedCollectionStats
import edu.gslis.eval.Qrels
import edu.gslis.indexes.IndexWrapper
import edu.gslis.indexes.IndexWrapperIndriImpl
import edu.gslis.queries.GQueriesFactory
import edu.gslis.queries.GQuery
import edu.gslis.scoring.expansion.StandardRM1Builder
import edu.gslis.searchhits.IndexBackedSearchHit
import edu.gslis.searchhits.SearchHit
import edu.gslis.utils.Stopper
import org.apache.commons.configuration.PropertiesConfiguration
import org.retrievable.documentExpansion.features.*
import org.retrievable.document_expansion.expansion.DocumentExpander
import org.retrievable.document_expansion.lms.LanguageModelEstimator
import org.retrievable.document_expansion.features.LMFeatures
import org.retrievable.document_expansion.scoring.ExpansionDocScorer


fun main(args: Array<String>) {
    // Load config
    val config = PropertiesConfiguration(args[0])

    // Load resources from config
    val index: IndexWrapper = IndexWrapperIndriImpl(config.getString("target-index"))
    val expansionIndex: IndexWrapper = IndexWrapperIndriImpl(config.getString("target-index"))
    val stopper = Stopper(config.getString("stoplist"))
    val queries = GQueriesFactory.getGQueries(config.getString("queries"))
    val qrels = Qrels(config.getString("qrels"), true, 1)

    // Load derived resources
    val expansionCollectionStats = IndexBackedCollectionStats()
    expansionCollectionStats.setStatSource(expansionIndex)
    val expander = DocumentExpander(expansionIndex, stopper)
    val expansionScorer = ExpansionDocScorer(expander)
    val rm1Builder = StandardRM1Builder(10, 20, expansionCollectionStats)

    queries.forEach { query: GQuery ->
        val results = index.runQuery(query, 1000)

        // For each judged document, compute:
        qrels.getPool(query.title).forEach { docno: String ->
            val document: SearchHit = IndexBackedSearchHit(index)
            document.docno = docno

            // Get expansion resources
            val expansionLM = LanguageModelEstimator.expansionLanguageModel(document, expansionScorer)
            val originalLM = LanguageModelEstimator.languageModel(document, index)
            val rm1 = rm1Builder.buildRelevanceModel(expander.createDocumentPseudoQuery(document), results, stopper)

            // Compute pre-expansion features
            val length = documentLength(document)
            val diversity = documentDiversity(document)
            val initialRank = documentRank(document, results)
            val clarity = documentClarity(document, index)

            // Compute post-expansion features
            val origToExpKL = LMFeatures.languageModelsKL(originalLM, expansionLM)
            val origToRMKL = LMFeatures.languageModelsKL(originalLM, rm1)
            val origToExpPerplexity = LMFeatures.perplexity(originalLM, expansionLM)
            val origToRMPerplexity = LMFeatures.perplexity(originalLM, rm1)
            val pairwiseShannonJensen = pairwiseSimilarity(expansionScorer.getExpansionDocs(document))

            // Print
            println(doubleArrayOf(length, diversity, initialRank.toDouble(), clarity, origToExpKL, origToRMKL, origToExpPerplexity, origToRMPerplexity, pairwiseShannonJensen).joinToString())
        }
    }
}