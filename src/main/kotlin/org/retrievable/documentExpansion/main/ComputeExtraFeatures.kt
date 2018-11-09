package org.retrievable.documentExpansion.main

import edu.gslis.docscoring.TextSimilarityMeasure
import edu.gslis.docscoring.support.CollectionStats
import edu.gslis.docscoring.support.IndexBackedCollectionStats
import edu.gslis.evaluation.evaluators.SetEvaluator
import edu.gslis.scoring.DirichletDocScorer
import edu.gslis.scoring.queryscoring.QueryLikelihoodQueryScorer
import edu.gslis.searchhits.IndexBackedSearchHit
import edu.gslis.utils.Stopper
import org.apache.commons.configuration.PropertiesConfiguration
import org.retrievable.documentExpansion.features.*
import org.retrievable.document_expansion.expansion.DocumentExpander
import org.retrievable.document_expansion.scoring.ExpansionDocScorer
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
    val collectionStats = HashMap<String, CollectionStats>()
    val queryScorers = HashMap<String, QueryLikelihoodQueryScorer>()
    val expansionQueryScorers = HashMap<String, QueryLikelihoodQueryScorer>()
    val docExpanders = HashMap<String, DocumentExpander>()

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
    println(headers.joinToString(","))

    var i = 0
    val features = hits.parallelStream().map {
        i++

        val index = it.indexName
        val docno = it.docno
        val query = switch.queries(index).getNamedQuery(it.queryTitle)

        System.err.println("Working on $docno/${query.title} ($i/${hits.size})")

        // Make the search hit
        val document = IndexBackedSearchHit(switch.index(index))
        document.docno = docno

        // Make the pseudo-query
        val docExpander = docExpanders.getOrPut(index) {
            val de = DocumentExpander(switch.index(index), stopper)
            de.setMaxNumDocs(10)
            de
        }
        val docPseudoQuery = docExpander.createDocumentPseudoQuery(document)

        // Make the expansion pseudo-query
        val expansionPseudoQuery = expansionPseudoQuery(document, docExpander, stopper)

        val expQueryScorer = expansionQueryScorers.getOrPut(index) { QueryLikelihoodQueryScorer(ExpansionDocScorer(docExpander)) }
        val queryScorer = queryScorers.getOrPut(index) {
            QueryLikelihoodQueryScorer(
                    DirichletDocScorer(collectionStats.getOrPut(index) {
                        val cs = IndexBackedCollectionStats()
                        cs.setStatSource(switch.index(index))
                        cs
                    })
            )
        }

        // Compute pre-expansion features
        val data = mapOf(
                "docno" to document.docno,
                "query" to query.title,
                "docRMRankChange" to documentRMRankChange(document, query, switch.index(index), stopper),
                "docRMScoreChange" to documentRMScoreChange(document, query, switch.index(index), stopper),
                "probFraction" to probabilityFraction(document, docPseudoQuery),
                "docSelfRetrievalRank" to documentSelfRetrievalRank(document, docPseudoQuery, switch.index(index)),
                "docSelfRetrievalScore" to documentSelfRetrievalScore(document, docPseudoQuery, switch.index(index)),
                "pseudoQueryExpansionProb" to expQueryScorer.scoreQuery(docPseudoQuery, document),
                "docPQexpPQJaccardSim" to SetEvaluator().jaccardSimilarity(docPseudoQuery.featureVector.features, expansionPseudoQuery.featureVector.features),
                "docPQexpPQCosineSim" to TextSimilarityMeasure.cosine(docPseudoQuery.featureVector, expansionPseudoQuery.featureVector, true),
                "expPseudoQueryClarity" to expansionPseudoQuery.featureVector.clarity(switch.index(index)),
                "expPseudoQueryOrigQL" to queryScorer.scoreQuery(expansionPseudoQuery, document)
        )

        // Print data
        headers.map { column -> data[column] }.joinToString(",")
    }

    print(features.toArray().joinToString("\n"))
}
