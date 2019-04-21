package org.retrievable.documentExpansion.main

import edu.gslis.docscoring.support.IndexBackedCollectionStats
import edu.gslis.eval.Qrels
import edu.gslis.indexes.IndexWrapperIndriImpl
import edu.gslis.queries.GQueriesFactory
import edu.gslis.queries.GQuery
import edu.gslis.scoring.DirichletDocScorer
import edu.gslis.scoring.expansion.StandardRM1Builder
import edu.gslis.scoring.queryscoring.QueryLikelihoodQueryScorer
import edu.gslis.searchhits.IndexBackedSearchHit
import edu.gslis.utils.Stopper
import org.apache.commons.configuration.PropertiesConfiguration
import org.retrievable.documentExpansion.features.*
import org.retrievable.document_expansion.expansion.DocumentExpander
import java.lang.System.`in`
import java.util.*


fun main(args: Array<String>) {
    // Load config
    val config = PropertiesConfiguration(args[0])
    /*val docsListFile = args[1]

    val hits = File(docsListFile).readLines().map {
        val parts = it.split(",")
        AnnotatedDocument(parts[0], parts[1])
    }*/

    // Load resources from config
    val stopper = Stopper(config.getString("stoplist"))
    val index = IndexWrapperIndriImpl(config.getString("target-index"))
    val queries = GQueriesFactory.getGQueries(config.getString("queries"))
    val qrels = Qrels(config.getString("qrels"), false, 1)

    // Load dependent resources
    val collectionStats = IndexBackedCollectionStats()
    collectionStats.setStatSource(index)
    val rM1Builder = StandardRM1Builder(collectionStats)
    val queryScorer = QueryLikelihoodQueryScorer(DirichletDocScorer(collectionStats))
    val docExpander = DocumentExpander(index, stopper)

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
    //println(headers.joinToString(","))

    val scanner = Scanner(`in`)
    while (scanner.hasNextLine()) {
        val parts = scanner.nextLine().split(",")
        val hit = AnnotatedDocument(parts[0], parts[1])

        val docno = hit.docno
        val query = queries.getNamedQuery(hit.queryTitle)

        // Compute RM1 from baseline hits
        val rm1Vec = rM1Builder.buildRelevanceModel(
                query,
                index.runQuery(query, StandardRM1Builder.DEFAULT_FEEDBACK_DOCS),
                stopper
        )
        val rm1 = GQuery()
        rm1.featureVector = rm1Vec

        // Make the search hit
        val document = IndexBackedSearchHit(index)
        document.docno = docno

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
                //"initialRank" to documentRank(document, baselineResults.getSearchHits(query)),
                "clarity" to documentSCS(document, index),
                "queryProminence" to queryProminence(document, query, docExpander),
                "rmImpQL" to rmImprovementQL(document, query, rm1, queryScorer ),
                //"rmImpRank" to rmImprovementRank(document, baselineResults.getSearchHits(query), rm1Results),
                "entropy" to documentEntropy(document),
                "avgIDF" to avgIDF(docPseudoQuery, index),
                "avgICTF" to avgICTF(docPseudoQuery, index),
                "scs" to scs(docPseudoQuery, index),
                "avgSCQ" to scqs.average(),
                "maxSCQ" to scqs.max(),
                "sumSCQ" to scqs.sum()
        )

        // Print data
        println(headers.map { column -> data[column] }.joinToString(","))
    }
}

data class AnnotatedDocument(val docno: String, val queryTitle: String)
