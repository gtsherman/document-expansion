package org.retrievable.documentExpansion.main

import edu.gslis.eval.Qrels
import edu.gslis.indexes.IndexWrapper
import edu.gslis.indexes.IndexWrapperIndriImpl
import edu.gslis.queries.GQueriesFactory
import edu.gslis.queries.GQuery
import edu.gslis.searchhits.IndexBackedSearchHit
import edu.gslis.searchhits.readTrecOutput
import org.apache.commons.configuration.PropertiesConfiguration
import org.retrievable.documentExpansion.features.documentClarity
import org.retrievable.documentExpansion.features.documentDiversity
import org.retrievable.documentExpansion.features.documentLength
import org.retrievable.documentExpansion.features.documentRank


fun main(args: Array<String>) {
    // Load config
    val config = PropertiesConfiguration(args[0])

    // Load resources from config
    val index: IndexWrapper = IndexWrapperIndriImpl(config.getString("target-index"))
    val queries = GQueriesFactory.getGQueries(config.getString("queries"))
    val qrels = Qrels(config.getString("qrels"), true, 1)
    val baselineResults = readTrecOutput(config.getString("baseline-run"))
    val expansionResults = readTrecOutput(config.getString("expansion-run"))

    println("docno,query,relevance,initialRank,rankImprovement,length,diversity,clarity")

    queries.forEach { query: GQuery ->
        // Make hits a set of documents a) retrieved for baseline, b) retrieved for expansion, c) in judgment pool
        val baselineHits = baselineResults.getSearchHits(query).hits().map { it.docno }
        val retrievedHits = baselineHits.intersect(expansionResults.getSearchHits(query).hits().map { it.docno })
        val judgedRetrievedHits = retrievedHits.intersect(qrels.getPool(query.title))

        // For each retrieved document, compute:
        judgedRetrievedHits.forEach { doc ->
            val document = IndexBackedSearchHit(index)
            document.docno = doc

            // Compute pre-expansion features
            val length = documentLength(document)
            val diversity = documentDiversity(document)
            val initialRank = documentRank(document, baselineResults.getSearchHits(query))
            val clarity = documentClarity(document, index)

            // Compute rank change
            val expansionRank = documentRank(document, expansionResults.getSearchHits(query))
            val rankChange = initialRank - expansionRank

            // Print data
            println(document.docno + "," +
                    query.title + "," +
                    doubleArrayOf(
                            qrels.getRelLevel(query.title, document.docno).toDouble(),
                            initialRank.toDouble(),
                            rankChange.toDouble(),
                            length,
                            diversity,
                            clarity
                    ).joinToString(",")
            )
        }
    }
}
