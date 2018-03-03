package org.retrievable.documentExpansion.main

import edu.gslis.docscoring.support.IndexBackedCollectionStats
import edu.gslis.eval.Qrels
import edu.gslis.indexes.IndexWrapperIndriImpl
import edu.gslis.queries.GQueries
import edu.gslis.queries.GQueriesFactory
import edu.gslis.scoring.DirichletDocScorer
import edu.gslis.scoring.DocScorer
import edu.gslis.searchhits.IndexBackedSearchHit
import edu.gslis.utils.Stopper
import org.apache.commons.configuration.PropertiesConfiguration
import org.retrievable.documentExpansion.analysis.*
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
    val stopper = Stopper(config.getString("stoplist"))

    // Command line resource
    val topicTermsData = readTopicTermsData(args[1])

    // Dependent resources
    val switch = CollectionSwitch(indexesDir, queriesDir, qrelsDir)
    val docExpander = DocumentExpander(expansionIndex, 20, stopper)
    val expansionDocScorer = ExpansionDocScorer(docExpander, 20)

    val userAnnotations = topicTermsData.annotationsBy("test")
    for (docno in userAnnotations.keys) {
        val collection = topicTermsData.collectionOf(docno)

        val topicTerms = userAnnotations.getOrDefault(docno, HashSet())

        val document = IndexBackedSearchHit(switch.index(collection))
        document.docno = docno

        val relevantQueries = switch.queries(collection).filter { query -> switch.qrels(collection).isRel(query.title, docno) }

        val probChange = probabilityChange(topicTerms, switch.scorer(collection), expansionDocScorer, document)
        val topicTermsChangeInAP = changeInAveragePrecision(topicTerms, document, switch.scorer(collection), expansionDocScorer, 20)
        val pseudoQueryRecall = pseudoQueryTermRecall(topicTerms, document, docExpander)
        val pseudoQueryVsTopicTerms = pseudoQueryVsTopicTermsResultsRecall(topicTerms, document, docExpander, 20)
        val queryTopicTermSimilarity = relevantQueries.map { query -> queryTopicTermSimilarity(topicTerms, query) }
        var queryTopicTermSimilarityString = queryTopicTermSimilarity.joinToString(",")

        if (queryTopicTermSimilarity.isEmpty()) {
            queryTopicTermSimilarityString += "-1.0"
        }

        println(
                docno + "," +
                doubleArrayOf(
                        probChange,
                        topicTermsChangeInAP,
                        pseudoQueryRecall,
                        pseudoQueryVsTopicTerms
                ).joinToString(",") + "," +
                queryTopicTermSimilarityString
        )
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
