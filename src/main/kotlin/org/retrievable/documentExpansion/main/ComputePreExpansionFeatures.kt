package org.retrievable.documentExpansion.main

import edu.gslis.docscoring.support.IndexBackedCollectionStats
import edu.gslis.eval.Qrels
import edu.gslis.indexes.IndexWrapper
import edu.gslis.indexes.IndexWrapperIndriImpl
import edu.gslis.queries.GQueriesFactory
import edu.gslis.queries.GQuery
import edu.gslis.scoring.DirichletDocScorer
import edu.gslis.scoring.expansion.StandardRM1Builder
import edu.gslis.scoring.queryscoring.QueryLikelihoodQueryScorer
import edu.gslis.searchhits.IndexBackedSearchHit
import edu.gslis.searchhits.IndexBackedSearchHits.convertToIndexBackedSearchHits
import edu.gslis.searchhits.readTrecOutput
import edu.gslis.utils.Stopper
import org.apache.commons.configuration.PropertiesConfiguration
import org.retrievable.documentExpansion.features.*
import org.retrievable.document_expansion.expansion.DocumentExpander


fun main(args: Array<String>) {
    // Load config
    val config = PropertiesConfiguration(args[0])

    // Load resources from config
    val index: IndexWrapper = IndexWrapperIndriImpl(config.getString("target-index"))
    val queries = GQueriesFactory.getGQueries(config.getString("queries"))
    val stopper = Stopper(config.getString("stoplist"))
    val qrels = Qrels(config.getString("qrels"), false, 1)
    val baselineResults = readTrecOutput(config.getString("baseline-run"))

    // Load dependent resources
    val collectionStats = IndexBackedCollectionStats()
    collectionStats.setStatSource(index)

    val docScorer = DirichletDocScorer(collectionStats)
    val queryScorer = QueryLikelihoodQueryScorer(docScorer)

    val rM1Builder = StandardRM1Builder(collectionStats)

    val docExpander = DocumentExpander(index, stopper)

    val headers = arrayOf(
            "docno",
            "query",
            "relevance",
            "initialRank",
            "length",
            "diversity",
            "clarity",
            "queryProminence",
            "rmImpQL",
            "rmImpRank",
            "entropy",
            "avgIDF",
            "avgICTF",
            "scs",
            "avgSCQ",
            "maxSCQ",
            "sumSCQ"
    )
    println(headers.joinToString(","))

    queries.forEach { query: GQuery ->
        // Make hits a set of documents a) retrieved for baseline, b) retrieved for expansion, c) in judgment pool
        val baselineHits = baselineResults.getSearchHits(query).hits().map { it.docno }

        // Compute RM1 from baseline hits
        val rm1Vec = rM1Builder.buildRelevanceModel(
                query,
                convertToIndexBackedSearchHits(baselineResults.getSearchHits(query), index),
                stopper
        )
        val rm1 = GQuery()
        rm1.featureVector = rm1Vec

        // Get RM1 results
        val rm1Results = index.runQuery(rm1, 1000)

        // For each retrieved document, compute:
        baselineHits.forEach { doc ->
            // Make the search hit
            val document = IndexBackedSearchHit(index)
            document.docno = doc

            // Make the pseudo-query
            val docPseudoQuery = docExpander.createDocumentPseudoQuery(document)

            // Get base metrics that will need to be averaged/summed/maxed
            val scqs = scq(docPseudoQuery, index)

            // Compute pre-expansion features
            val data = mapOf(
                    "docno" to document.docno,
                    "query" to query.title,
                    "relevance" to qrels.getRelLevel(query.title, document.docno),
                    "length" to documentLength(document),
                    "diversity" to documentDiversity(document),
                    "initialRank" to documentRank(document, baselineResults.getSearchHits(query)),
                    "clarity" to documentSCS(document, index),
                    "queryProminence" to queryProminence(document, query, docExpander),
                    "rmImpQL" to rmImprovementQL(document, query, rm1, queryScorer),
                    "rmImpRank" to rmImprovementRank(document, baselineResults.getSearchHits(query), rm1Results),
                    "entropy" to documentEntropy(document),
                    "avgIDF" to avgIDF(docPseudoQuery, index),
                    "avgICTF" to avgICTF(docPseudoQuery, index),
                    "scs" to scs(docPseudoQuery, index),
                    "avgSCQ" to scqs.average(),
                    "maxSCQ" to scqs.max(),
                    "sumSCQ" to scqs.sum()
            )

            // Print data
            println(headers.map { data[it] }.joinToString(","))
        }
    }
}
