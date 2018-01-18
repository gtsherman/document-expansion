package org.retrievable.documentExpansion.main

import edu.gslis.eval.Qrels
import edu.gslis.indexes.IndexWrapper
import edu.gslis.indexes.IndexWrapperIndriImpl
import edu.gslis.queries.GQueriesFactory
import edu.gslis.queries.GQuery
import edu.gslis.searchhits.IndexBackedSearchHit
import edu.gslis.searchhits.SearchHit
import edu.gslis.searchhits.readTrecOutput
import org.apache.commons.configuration.PropertiesConfiguration
import org.retrievable.documentExpansion.features.documentClarity
import org.retrievable.documentExpansion.features.documentDiversity
import org.retrievable.documentExpansion.features.documentRank
import org.retrievable.documentExpansion.features.documentLength


fun main(args: Array<String>) {
    // Load config
    val config = PropertiesConfiguration(args[0])

    // Load resources from config
    val index: IndexWrapper = IndexWrapperIndriImpl(config.getString("target-index"))
    val queries = GQueriesFactory.getGQueries(config.getString("queries"))
    val qrels = Qrels(config.getString("qrels"), true, 1)
    val baselineResults = readTrecOutput(config.getString("baseline-run"))
    val expansionResults = readTrecOutput(config.getString("expansion-run"))

    queries.forEach { query: GQuery ->

        // For each judged document, compute:
        qrels.getPool(query.title).forEach { docno: String ->
            val document: SearchHit = IndexBackedSearchHit(index)
            document.docno = docno

            // Compute pre-expansion features
            val length = documentLength(document)
            val diversity = documentDiversity(document)
            val initialRank = documentRank(document, baselineResults.getSearchHits(query))
            val clarity = documentClarity(document, index)

            // Compute rank change
            val expansionRank = documentRank(document, expansionResults.getSearchHits(query))
            var rankChange = initialRank - expansionRank
            if (initialRank === 0 || expansionRank === 0) {
                rankChange = -1
            }

            // Print
            println("docno,rankChange,length,diversity,initialRank,clarity")
            println(docno + ", " +
                    doubleArrayOf(
                            rankChange.toDouble(),
                            length,
                            diversity,
                            initialRank.toDouble(),
                            clarity
                    ).joinToString(",")
            )
        }
    }
}
