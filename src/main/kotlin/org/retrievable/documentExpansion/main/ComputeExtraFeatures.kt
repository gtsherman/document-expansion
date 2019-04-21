package org.retrievable.documentExpansion.main

import edu.gslis.docscoring.TextSimilarityMeasure
import edu.gslis.docscoring.support.IndexBackedCollectionStats
import edu.gslis.evaluation.evaluators.SetEvaluator
import edu.gslis.indexes.IndexWrapperIndriImpl
import edu.gslis.queries.GQueriesFactory
import edu.gslis.scoring.DirichletDocScorer
import edu.gslis.scoring.queryscoring.QueryLikelihoodQueryScorer
import edu.gslis.searchhits.IndexBackedSearchHit
import edu.gslis.utils.Stopper
import org.apache.commons.configuration.PropertiesConfiguration
import org.retrievable.documentExpansion.features.*
import org.retrievable.document_expansion.expansion.DocumentExpander
import org.retrievable.document_expansion.scoring.ExpansionDocScorer
import java.io.File
import java.util.*


fun main(args: Array<String>) {
    // Load config
    val config = PropertiesConfiguration(args[0])
    val docListFile = args[1]

    // Load resources from config
    val stopper = Stopper(config.getString("stoplist"))
    val index = IndexWrapperIndriImpl(config.getString("target-index"))
    val queries = GQueriesFactory.getGQueries(config.getString("queries"))

    val hits = File(docListFile).readLines().map {
        val parts = it.split(",")
        AnnotatedDocument(parts[0], parts[1])
    }

    // Load dependent resources
    val collectionStats = IndexBackedCollectionStats()
    collectionStats.setStatSource(index)
    val docExpander = DocumentExpander(index, stopper)
    docExpander.setMaxNumDocs(10)
    val queryScorer = QueryLikelihoodQueryScorer(DirichletDocScorer(collectionStats))
    val expansionQueryScorer = QueryLikelihoodQueryScorer(ExpansionDocScorer(docExpander))

    val headers = arrayOf(
            "docno",
            "query",
            "docRMRankChange",
            "docRMScoreChange",
            "probFraction",
            "docSelfRetrievalRank",
            "docSelfRetrievalScore",
            "pseudoQueryExpansionProb",
            "docPQexpPQJaccardSim",
            "docPQexpPQCosineSim",
            "expPseudoQueryClarity",
            "expPseudoQueryOrigQL"
    )
    //println(headers.joinToString(","))

    val scanner = Scanner(System.`in`)
    while (scanner.hasNextLine()) {
        val parts = scanner.nextLine().split(",")
        val hit = AnnotatedDocument(parts[0], parts[1])

        val docno = hit.docno
        val query = queries.getNamedQuery(hit.queryTitle)

        // Make the search hit
        val document = IndexBackedSearchHit(index)
        document.docno = docno

        // Make the pseudo-query
        val docPseudoQuery = docExpander.createDocumentPseudoQuery(document)

        // Make the expansion pseudo-query
        val expansionPseudoQuery = expansionPseudoQuery(document, docExpander, stopper)

        // Compute pre-expansion features
        val data = mapOf(
                "docno" to document.docno,
                "query" to query.title,
                "docRMRankChange" to documentRMRankChange(document, query, index, stopper),
                "docRMScoreChange" to documentRMScoreChange(document, query, index, stopper),
                "probFraction" to probabilityFraction(document, docPseudoQuery),
                "docSelfRetrievalRank" to documentSelfRetrievalRank(document, docPseudoQuery, index),
                "docSelfRetrievalScore" to documentSelfRetrievalScore(document, docPseudoQuery, index),
                "pseudoQueryExpansionProb" to expansionQueryScorer.scoreQuery(docPseudoQuery, document),
                "docPQexpPQJaccardSim" to SetEvaluator().jaccardSimilarity(docPseudoQuery.featureVector.features, expansionPseudoQuery.featureVector.features),
                "docPQexpPQCosineSim" to TextSimilarityMeasure.cosine(docPseudoQuery.featureVector, expansionPseudoQuery.featureVector, true),
                "expPseudoQueryClarity" to expansionPseudoQuery.featureVector.clarity(index),
                "expPseudoQueryOrigQL" to queryScorer.scoreQuery(expansionPseudoQuery, document)
        )

        // Print data
        println(headers.map { column -> data[column] }.joinToString(","))
    }
}
