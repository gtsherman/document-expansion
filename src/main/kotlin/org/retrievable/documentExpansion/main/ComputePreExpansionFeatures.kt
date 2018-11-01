package org.retrievable.documentExpansion.main

import edu.gslis.docscoring.support.CollectionStats
import edu.gslis.docscoring.support.IndexBackedCollectionStats
import edu.gslis.queries.GQuery
import edu.gslis.scoring.DirichletDocScorer
import edu.gslis.scoring.expansion.StandardRM1Builder
import edu.gslis.scoring.queryscoring.QueryLikelihoodQueryScorer
import edu.gslis.searchhits.IndexBackedSearchHit
import edu.gslis.utils.Stopper
import org.apache.commons.configuration.PropertiesConfiguration
import org.retrievable.documentExpansion.features.*
import org.retrievable.document_expansion.expansion.DocumentExpander
import java.io.File


fun main(args: Array<String>) {
    // Load config
    val config = PropertiesConfiguration(args[0])

    // Load resources from config
    val switch = CollectionSwitch(config.getString("indexes-dir"), config.getString("queries-dir"), config.getString("qrels-dir"))
    val stopper = Stopper(config.getString("stoplist"))
    //val baselineResults = readTrecOutput(config.getString("baseline-run"))

    val hits = File(args[1]).readLines().map {
        val parts = it.split(",")
        AnnotatedDocument(parts[0], parts[1], parts[2])
    }

    // Load dependent resources
    val rM1Builders = HashMap<String, StandardRM1Builder>()
    val collectionStats = HashMap<String, CollectionStats>()
    val queryScorers = HashMap<String, QueryLikelihoodQueryScorer>()
    val docExpanders = HashMap<String, DocumentExpander>()

    val headers = arrayOf(
            "docno",
            "query",
            "relevance",
            //"initialRank",
            "length",
            "diversity",
            "clarity",
            "queryProminence",
            "rmImpQL",
            //"rmImpRank",
            "entropy",
            "avgIDF",
            "avgICTF",
            "scs",
            "avgSCQ",
            "maxSCQ",
            "sumSCQ"
    )
    println(headers.joinToString(","))

    var i = 0
    val features = hits.parallelStream().map {
        i++

        val index = it.indexName
        val docno = it.docno
        val query = switch.queries(index).getNamedQuery(it.queryTitle)

        System.err.println("Working on $docno/${query.title} ($i/${hits.size})")

        // Compute RM1 from baseline hits
        val rM1Builder = rM1Builders.getOrPut(index) {
            val thisCollectionStats = collectionStats.getOrPut(index) {
                val tcs = IndexBackedCollectionStats()
                tcs.setStatSource(switch.index(index))
                tcs
            }
            StandardRM1Builder(thisCollectionStats)
        }

        val rm1Vec = rM1Builder.buildRelevanceModel(
                query,
                switch.index(index).runQuery(query, StandardRM1Builder.DEFAULT_FEEDBACK_DOCS),
                stopper
        )
        val rm1 = GQuery()
        rm1.featureVector = rm1Vec

        // Make the search hit
        val document = IndexBackedSearchHit(switch.index(index))
        document.docno = docno

        // Make the pseudo-query
        val docExpander = docExpanders.getOrPut(index) {
            DocumentExpander(switch.index(index), stopper)
        }
        val docPseudoQuery = docExpander.createDocumentPseudoQuery(document)

        // Get base metrics that will need to be averaged/summed/maxed
        val scqs = scq(docPseudoQuery, switch.index(index))

        // Compute pre-expansion features
        val data = mapOf(
                "docno" to document.docno,
                "query" to query.title,
                "relevance" to switch.qrels(index).getRelLevel(query.title, document.docno),
                "length" to documentLength(document),
                "diversity" to documentDiversity(document),
                //"initialRank" to documentRank(document, baselineResults.getSearchHits(query)),
                "clarity" to documentSCS(document, switch.index(index)),
                "queryProminence" to queryProminence(document, query, docExpander),
                "rmImpQL" to rmImprovementQL(document, query, rm1, queryScorers.getOrPut(index) { QueryLikelihoodQueryScorer(DirichletDocScorer(collectionStats[index])) }),
                //"rmImpRank" to rmImprovementRank(document, baselineResults.getSearchHits(query), rm1Results),
                "entropy" to documentEntropy(document),
                "avgIDF" to avgIDF(docPseudoQuery, switch.index(index)),
                "avgICTF" to avgICTF(docPseudoQuery, switch.index(index)),
                "scs" to scs(docPseudoQuery, switch.index(index)),
                "avgSCQ" to scqs.average(),
                "maxSCQ" to scqs.max(),
                "sumSCQ" to scqs.sum()
        )

        // Print data
        headers.map { column -> data[column] }.joinToString(",")
    }

    print(features.toArray().joinToString("\n"))
}

data class AnnotatedDocument(val docno: String, val queryTitle: String, val indexName: String)
