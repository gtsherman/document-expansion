package org.retrievable.documentExpansion.main

import edu.gslis.docscoring.support.IndexBackedCollectionStats
import edu.gslis.evaluation.evaluators.SetEvaluator
import edu.gslis.indexes.CachedFeatureVectorIndexWrapperIndriImpl
import edu.gslis.indexes.IndexWrapper
import edu.gslis.indexes.IndexWrapperIndriImpl
import edu.gslis.queries.GQueriesFactory
import edu.gslis.queries.GQuery
import edu.gslis.scoring.DirichletDocScorer
import edu.gslis.scoring.DocScorer
import edu.gslis.scoring.expansion.StandardRM1Builder
import edu.gslis.searchhits.IndexBackedSearchHit
import edu.gslis.searchhits.SearchHits
import edu.gslis.textrepresentation.FeatureVector
import edu.gslis.utils.Stopper
import org.apache.commons.configuration.PropertiesConfiguration
import org.retrievable.document_expansion.expansion.DocumentExpander
import java.io.File
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

fun pseudoAveragePrecision(results: List<String>, relDocs: Set<String>): Double {
    if (relDocs.isEmpty())
        return 0.0

    var ap = 0.0
    var rels = 0
    var seen = 0

    for (result in results) {
        seen++

        if (relDocs.contains(result)) {
            rels++
            ap += rels / seen.toDouble()
        }
    }

    ap /= relDocs.size

    if (java.lang.Double.isNaN(ap))
        ap = 0.0
    return ap
}

fun clarity(vector: FeatureVector, index: IndexWrapper) : Double {
    return vector.clarity(index)
}

fun cosineSimilarity(vector1: FeatureVector, vector2: FeatureVector) : Double {
    val numerator = vector1.features.intersect(vector2).map { term ->
        vector1.getFeatureWeight(term) / vector1.length * vector2.getFeatureWeight(term) / vector2.length
    }.sum()

    val denom1 = sqrt(vector1.map { (vector1.getFeatureWeight(it) / vector1.length).pow(2) }.sum())
    val denom2 = sqrt(vector2.map { (vector2.getFeatureWeight(it) / vector2.length).pow(2) }.sum())

    return try {
        numerator / (denom1 * denom2)
    } catch (e: ArithmeticException) {
        0.0
    }
}

fun wig(query: GQuery, index: IndexWrapper, scorer: DocScorer, topResults: SearchHits) : Double {
    var wig = 0.0
    val lam = 1 / sqrt(query.featureVector.length)
    topResults.hits().forEach { doc ->
        query.featureVector.forEach { term ->
            val pDoc = scorer.scoreTerm(term, doc)
            val pCol = (index.termFreq(term) + 1) / index.termCount()
            wig += lam * log10(pDoc / pCol!!)
        }
    }

    return wig / topResults.size().toDouble()
}

fun avgIDF(query: GQuery, index: IndexWrapper) : Double {
    return query.featureVector.map { term ->
        log10(index.docCount() / (index.docFreq(term) + 1))
    }.average()
}

fun main(args: Array<String>) {
    val config = PropertiesConfiguration(args[0])
    val queryTarget = args[1]
    val have = args[2]
    val runs = args.slice(3 until args.size)

    val targetIndex = IndexWrapperIndriImpl(config.getString("target-index"))
    val expansionIndexNames = config.getStringArray("expansion-index")
    val expansionIndexes = expansionIndexNames.map { CachedFeatureVectorIndexWrapperIndriImpl(it) }

    val queries = GQueriesFactory.getGQueries(config.getString("queries"))
    val stopper = Stopper(config.getString("stoplist"))

    val query = queries.first { it.title == queryTarget }
    query.applyStopper(stopper)

    val haveDocSet = HashSet<String>()
    File(have).readLines().forEach { line ->
        val parts = line.split(",")
        val docno = parts[1]
        haveDocSet.add(docno)
    }

    val docSet = HashSet<String>()
    runs.forEach { file ->
        val lines = File(file).readLines()
        lines.forEach { line ->
            val parts = line.split(" ")
            val queryTitle = parts[0]
            //val rank = Integer.parseInt(parts[3])
            val doc = parts[2]
            if (queryTitle == query.title && !haveDocSet.contains(doc)) {
                docSet.add(doc)
            }
        }
    }

    val setEval = SetEvaluator()

    val targetQueryResults = targetIndex.runQuery(query, 10)

    val collectionStats = IndexBackedCollectionStats()
    collectionStats.setStatSource(targetIndex)
    val rm1 = StandardRM1Builder(10, 20, collectionStats).buildRelevanceModel(query, targetQueryResults, stopper)

    val docExpanders = expansionIndexes.map { DocumentExpander(it, 20, stopper) }

    val queryResults = expansionIndexes.map { it.runQuery(query, 10) }
    val queryResultsSets = queryResults.map { it.hits().map { it.docno }.toSet() }
    val expansionCollectionStatistics = expansionIndexes.map { val cs = IndexBackedCollectionStats(); cs.setStatSource(it); cs }
    //val rm1Builders = collectionStatistics.map { StandardRM1Builder(10, 10, it) }
    //val expansionRMs = rm1Builders.mapIndexed { i, rm1Builder -> rm1Builder.buildRelevanceModel(query, queryResults[i], stopper) }
    val docScorers = expansionIndexes.mapIndexed { i, index -> DirichletDocScorer(expansionCollectionStatistics[i]) }

    val queryExpansionCollectionLikelihoods = expansionIndexes.map { expansionIndex ->
        query.featureVector.features.associate { term ->
            term to ((expansionIndex.termFreq(term) + 1) / expansionIndex.termCount()).pow(query.featureVector.getFeatureWeight(term))
        }
    }
    val expansionIndexQLs = queryExpansionCollectionLikelihoods.map { it.values.reduce(Double::times) }

    println("query,doc,index,jaccard,pseudo_map,doc_clarity,target_rm,exp_ql,doc_vs_exp,wig,avg_idf")
    docSet.forEach { docno ->
        val document = IndexBackedSearchHit(targetIndex)
        document.docno = docno

        //document.featureVector.forEach { term -> document.featureVector.setTerm(term, document.featureVector.getFeatureWeight(term) / document.featureVector.length) }

        expansionIndexes.forEachIndexed { i, expansionIndex ->
            val pseudoQuery = docExpanders[i].getPseudoQuery(document)

            val expansionDocs = docExpanders[i].expandDocument(document, 10)
            val expansionDocsSet = expansionDocs.map { it.docno }.toSet()

            val expansionPseudoDocVector = FeatureVector(stopper)
            expansionDocs.forEach {
                it.featureVector.forEach { term ->
                    expansionPseudoDocVector.addTerm(term, it.featureVector.getFeatureWeight(term))
                }
            }

            val jaccard = setEval.jaccardSimilarity(expansionDocsSet, queryResultsSets[i])
            val pseudoAP = pseudoAveragePrecision(expansionDocs.map { it.docno }, queryResultsSets[i])
            val docClarity = clarity(pseudoQuery.featureVector, expansionIndex)
            val targetRMSimilarity = cosineSimilarity(rm1, expansionPseudoDocVector)
            val docToExpansionSimilarity = cosineSimilarity(pseudoQuery.featureVector, expansionPseudoDocVector)
            val weightedInfoGain = wig(pseudoQuery, expansionIndex, docScorers[i], expansionDocs)
            val averageIDF = avgIDF(pseudoQuery, expansionIndex)

            println(
                    listOf(query.title, docno, expansionIndexNames[i],
                            jaccard, pseudoAP, docClarity, targetRMSimilarity, expansionIndexQLs[i],
                            docToExpansionSimilarity, weightedInfoGain, averageIDF).joinToString(",")
            )
        }
    }
}
