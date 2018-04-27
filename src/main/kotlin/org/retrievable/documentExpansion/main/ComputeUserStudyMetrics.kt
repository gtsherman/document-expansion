package org.retrievable.documentExpansion.main

import edu.gslis.docscoring.support.IndexBackedCollectionStats
import edu.gslis.eval.Qrels
import edu.gslis.indexes.IndexWrapperIndriImpl
import edu.gslis.queries.GQueries
import edu.gslis.queries.GQueriesFactory
import edu.gslis.queries.GQuery
import edu.gslis.scoring.DirichletDocScorer
import edu.gslis.scoring.DocScorer
import edu.gslis.scoring.InterpolatedDocScorer
import edu.gslis.scoring.queryscoring.QueryLikelihoodQueryScorer
import edu.gslis.searchhits.IndexBackedSearchHit
import edu.gslis.textrepresentation.FeatureVector
import edu.gslis.utils.Stopper
import org.apache.commons.configuration.PropertiesConfiguration
import org.apache.commons.math3.exception.NumberIsTooSmallException
import org.apache.commons.math3.stat.inference.TTest
import org.retrievable.documentExpansion.analysis.*
import org.retrievable.documentExpansion.utils.OptimalParameters
import org.retrievable.document_expansion.expansion.DocumentExpander
import org.retrievable.document_expansion.scoring.ExpansionDocScorer
import java.io.File
import java.nio.file.Paths


fun main(args: Array<String>) {
    // Load config
    val config = PropertiesConfiguration(args[0])

    // Load resources from config
    val expansionIndex = IndexWrapperIndriImpl(config.getString("expansion-index"))
    val indexesDir = config.getString("indexes-dir")
    val queriesDir = config.getString("queries-dir")
    val qrelsDir = config.getString("qrels-dir")
   // val unstemmedStopper = Stopper(config.getString("stoplist"))
    val stopper = Stopper(config.getString("stoplist"))
    //stemText(unstemmedStopper.asSet()).forEach { stopper.addStopword(it) }

    // Command line resource
    val topicTermsData = readTopicTermsData(args[1])

    // Dependent resources
    val switch = CollectionSwitch(indexesDir, queriesDir, qrelsDir)

    println(
            arrayOf("docno",
                    "query",
                    "probChange",
                    "totalProbTarget",
                    "totalProbExpansion",
                    "totalProbExpanded",
                    "qtotalProbTarget",
                    "qtotalProbExpansion",
                    "qtotalProbExpanded",
                    "totalProbTargetQL",
                    "totalProbExpansionQL",
                    "totalProbExpandedQL",
                    "qtotalProbTargetQL",
                    "qtotalProbExpansionQL",
                    "qtotalProbExpandedQL",
                    "ttProbTtest",
                    "qProbTtest",
                    "ttAPTarget",
                    "ttAPExpansion",
                    "ttAPExpanded",
                    "qAPTarget",
                    "qAPExpansion",
                    "qAPExpanded",
                    "pqTTRecall",
                    "qpqRecall",
                    "pqTTResultsRecall",
                    "qpqResultsRecall",
                    "qTTSim").joinToString(",")
    )

    val userAnnotations = topicTermsData.annotationsBy("test")
    for (docno in userAnnotations.keys) {
        val collection = topicTermsData.collectionOf(docno)

        val topicTerms = userAnnotations.getOrDefault(docno, HashSet())
        val topicTermsQuery = GQuery()
        topicTermsQuery.featureVector = FeatureVector(null)
        topicTerms.forEach { topicTermsQuery.featureVector.addTerm(it) }

        val document = IndexBackedSearchHit(switch.index(collection))
        document.docno = docno

        //val relevantQueries = switch.queries(collection).filter { query -> switch.qrels(collection).isRel(query.title, docno) }
        val relevantQueries = switch.queries(collection).filter { query -> switch.qrels(collection).getNonRelDocs(query.title).contains(docno) }
        for (relevantQuery in relevantQueries) {
            relevantQuery.applyStopper(stopper)

            val optimalParameters = OptimalParameters(File("/home/gsherma2/doc-exp/out/ap/wiki/optimal_perq"), relevantQuery.title)

            val docExpander = DocumentExpander(expansionIndex, optimalParameters.numTerms, stopper)
            val expansionDocScorer = ExpansionDocScorer(docExpander, optimalParameters.numDocs)
            val interpScorer = InterpolatedDocScorer(mapOf(switch.scorer(collection) to optimalParameters.origWeight, expansionDocScorer to 1 - optimalParameters.origWeight))
            val queryScorer = QueryLikelihoodQueryScorer(switch.scorer(collection))
            val expQueryScorer = QueryLikelihoodQueryScorer(expansionDocScorer)
            val fullQueryScorer = QueryLikelihoodQueryScorer(interpScorer)

            //val relevantQueries = switch.queries(collection).filter { query -> switch.qrels(collection).isRel(query.title, docno) }

            val probChange = probabilityChange(topicTerms, switch.scorer(collection), expansionDocScorer, document)
            val totalProbTarget = totalProbability(topicTerms, switch.scorer(collection), document)
            val totalProbExpansion = totalProbability(topicTerms, expansionDocScorer, document)
            val totalProbExpanded = totalProbability(topicTerms, interpScorer, document)
            val qtotalProbTarget = totalProbability(relevantQuery.featureVector.features, switch.scorer(collection), document)
            val qtotalProbExpansion = totalProbability(relevantQuery.featureVector.features, expansionDocScorer, document)
            val qtotalProbExpanded = totalProbability(relevantQuery.featureVector.features, interpScorer, document)
            val totalProbTargetQL = queryScorer.scoreQuery(topicTermsQuery, document)
            val totalProbExpansionQL = expQueryScorer.scoreQuery(topicTermsQuery, document)
            val totalProbExpandedQL = fullQueryScorer.scoreQuery(topicTermsQuery, document)
            val qtotalProbTargetQL = queryScorer.scoreQuery(relevantQuery, document)
            val qtotalProbExpansionQL = expQueryScorer.scoreQuery(relevantQuery, document)
            val qtotalProbExpandedQL = fullQueryScorer.scoreQuery(relevantQuery, document)
            var ttProbTtest: Double
            try {
                ttProbTtest = TTest().pairedTTest(
                        topicTerms
                                .toSet()
                                .sorted()
                                .map { expansionDocScorer.scoreTerm(it, document) }
                                .toDoubleArray(),
                        topicTerms
                                .toSet()
                                .sorted()
                                .map { switch.scorer(collection).scoreTerm(it, document) }
                                .toDoubleArray()
                )
            } catch (e: NumberIsTooSmallException) {
                ttProbTtest = -1.0
            }
            var qProbTtest: Double
            try {
                qProbTtest = TTest().pairedTTest(
                        relevantQuery.featureVector.features
                                .toSet()
                                .sorted()
                                .map { expansionDocScorer.scoreTerm(it, document) }
                                .toDoubleArray(),
                        relevantQuery.featureVector.features
                                .toSet()
                                .sorted()
                                .map { switch.scorer(collection).scoreTerm(it, document) }
                                .toDoubleArray()
                )
            } catch (e: NumberIsTooSmallException) {
                qProbTtest = -1.0
            }
            val topicTermsAPTarget = topicTermsAveragePrecision(topicTerms, document, switch.scorer(collection), stopper)
            val topicTermsAPExpansion = topicTermsAveragePrecision(topicTerms, document, expansionDocScorer, stopper)
            val topicTermsAPExpanded = topicTermsAveragePrecision(topicTerms, document, interpScorer, stopper)
            val qtopicTermsAPTarget = topicTermsAveragePrecision(relevantQuery.featureVector.features, document, switch.scorer(collection), stopper)
            val qtopicTermsAPExpansion = topicTermsAveragePrecision(relevantQuery.featureVector.features, document, expansionDocScorer, stopper)
            val qtopicTermsAPExpanded = topicTermsAveragePrecision(relevantQuery.featureVector.features, document, interpScorer, stopper)
            val pseudoQueryRecall = pseudoQueryTermRecall(topicTerms, document, docExpander)
            val qpseudoQueryRecall = pseudoQueryTermRecall(relevantQuery.featureVector.features, document, docExpander)
            val pseudoQueryVsTopicTerms = pseudoQueryVsTopicTermsResultsRecall(topicTerms, document, docExpander, optimalParameters.numDocs)
            val qpseudoQueryVsTopicTerms = pseudoQueryVsTopicTermsResultsRecall(relevantQuery.featureVector.features, document, docExpander, optimalParameters.numDocs)
            //val queryTopicTermSimilarity = relevantQueries.map { query -> queryTopicTermSimilarity(topicTerms, query, stopper, stem = true) }
            val queryTopicTermSimilarity = queryTopicTermSimilarity(topicTerms, relevantQuery, stopper)
            var queryTopicTermSimilarityString = queryTopicTermSimilarity

            /*if (queryTopicTermSimilarity.isEmpty()) {
            queryTopicTermSimilarityString += "-1.0"
        }*/

            println(
                    "$docno,${relevantQuery.title}," +
                            doubleArrayOf(
                                    probChange,
                                    totalProbTarget,
                                    totalProbExpansion,
                                    totalProbExpanded,
                                    qtotalProbTarget,
                                    qtotalProbExpansion,
                                    qtotalProbExpanded,
                                    totalProbTargetQL,
                                    totalProbExpansionQL,
                                    totalProbExpandedQL,
                                    qtotalProbTargetQL,
                                    qtotalProbExpansionQL,
                                    qtotalProbExpandedQL,
                                    ttProbTtest,
                                    qProbTtest,
                                    topicTermsAPTarget,
                                    topicTermsAPExpansion,
                                    topicTermsAPExpanded,
                                    qtopicTermsAPTarget,
                                    qtopicTermsAPExpansion,
                                    qtopicTermsAPExpanded,
                                    pseudoQueryRecall,
                                    qpseudoQueryRecall,
                                    //pseudoQueryJaccard,
                                    pseudoQueryVsTopicTerms,
                                    qpseudoQueryVsTopicTerms
                                    //pseudoQueryVsTopicTermsJaccard
                            ).joinToString(",") + "," +
                            queryTopicTermSimilarityString
            )
        }
    }
}


fun readTopicTermsData(fileName: String) : TopicTerms {
    val file = File(fileName)
    val topicTermList = ArrayList<TopicTerm>()
    file.forEachLine { line ->
        val data = line.trim().split(",")
        val topicTerm = TopicTerm(data[0], data[1], data[2], data[3])
        topicTermList.add(topicTerm)
    }
    return TopicTerms(topicTermList)
}


data class TopicTerm(val user: String, val docno: String, val index: String, val term: String)


class TopicTerms(val topicTerms: Collection<TopicTerm>) {

    /**
     * @return A docno->termSet map containing all of the terms assigned to each docno by a given user
     */
    fun annotationsBy(user: String) : Map<String, Set<String>> {
        return topicTerms
                .filter { it.user == user }
                .map { Pair(it.docno, it.term) }
                .groupBy { it.first }
                .mapValues {
                    it.value.map { pair -> pair.second }.toSet()
                }
    }

    /**
     * @return A set of the terms assigned by all users for the given docno
     */
    fun topicTermsFor(docno: String) : Set<String> {
        return topicTerms
                .filter { it.docno == docno }
                .map { it.term }
                .toSet()
    }

    fun collectionOf(docno: String) : String {
        return topicTerms.filter { it.docno == docno }.map { it.index }.toSet().first()
    }

}

private class CollectionSwitch(val indexesDir: String, val queriesDir: String, val qrelsDir: String) {
    private val AP = "AP_88-89"
    private val ROBUST = "robust"
    private val WT10G = "wt10g"

    private val indexes = HashMap<String, IndexWrapperIndriImpl>()
    private val queries = HashMap<String, GQueries>()
    private val qrels = HashMap<String, Qrels>()
    private val stats = HashMap<String, IndexBackedCollectionStats>()
    private val scorers = HashMap<String, DocScorer>()

    fun index(id: String) : IndexWrapperIndriImpl {
        return indexes.getOrPut(id, { IndexWrapperIndriImpl(Paths.get(indexesDir, id).toString()) })
    }

    fun queries(id: String) : GQueries {
        val queriesName = when (id) {
            AP -> "101-200"
            ROBUST -> "robust"
            WT10G -> "451-550"
            else -> "" // blah :(
        }
        return queries.getOrPut(id, { GQueriesFactory.getGQueries(Paths.get(queriesDir, "topics.$queriesName.json").toString()) })
    }

    fun qrels(id: String) : Qrels {
        val qrelsName = when (id) {
            AP -> "ap"
            ROBUST -> "robust"
            WT10G -> "wt10g"
            else -> "" // blah :(
        }
        return qrels.getOrPut(id, { Qrels(Paths.get(qrelsDir, "qrels.$qrelsName").toString(), true, 1) })
    }

    fun stats(id: String) : IndexBackedCollectionStats {
        return stats.getOrPut(id, { val cs = IndexBackedCollectionStats(); cs.setStatSource(index(id)); cs })
    }

    fun scorer(id: String) : DocScorer {
        return scorers.getOrPut(id, { DirichletDocScorer(stats(id)) })
    }

}
