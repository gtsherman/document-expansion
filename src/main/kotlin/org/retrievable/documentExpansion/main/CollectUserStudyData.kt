package org.retrievable.documentExpansion.main

import edu.gslis.docscoring.support.IndexBackedCollectionStats
import edu.gslis.eval.Qrels
import edu.gslis.indexes.IndexWrapperIndriImpl
import edu.gslis.queries.GQueriesFactory
import edu.gslis.scoring.expansion.StandardRM1Builder
import edu.gslis.searchhits.IndexBackedSearchHit
import edu.gslis.searchhits.SearchHits
import edu.gslis.textrepresentation.FeatureVector
import edu.gslis.utils.Stopper
import org.apache.commons.configuration.PropertiesConfiguration
import org.apache.commons.lang.StringUtils
import org.retrievable.documentExpansion.data.sampleDocuments
import org.retrievable.documentExpansion.data.sampleTerms
import org.retrievable.document_expansion.expansion.DocumentExpander


fun main(args: Array<String>) {
    // Load config
    val config = PropertiesConfiguration(args[0])

    // Load resources from config
    val targetIndex = IndexWrapperIndriImpl(config.getString("target-index"))
    val expansionIndex = IndexWrapperIndriImpl(config.getString("expansion-index"))
    val queries = GQueriesFactory.getGQueries(config.getString("queries"))
    val stopper = Stopper(config.getString("stoplist"))
    val qrels = Qrels(config.getString("qrels"), true, 1)
    val sampleSize = Integer.parseInt(config.getString("sample-size", "500"))

    // Dependent resources
    val targetCollectionStats = IndexBackedCollectionStats()
    targetCollectionStats.setStatSource(targetIndex)
    val expansionCollectionStats = IndexBackedCollectionStats()
    expansionCollectionStats.setStatSource(expansionIndex)

    val targetRM1Builder = StandardRM1Builder(20, 20, targetCollectionStats)
    val expansionRM1Builder = StandardRM1Builder(20, 20, expansionCollectionStats)

    val documentExpander = DocumentExpander(expansionIndex, 10, stopper)

    val relDocs = SearchHits(
            queries
                    .map { query -> qrels.getRelDocs(query.title) }
                    .reduce { collected, thisQueryDocs -> collected union thisQueryDocs }
                    .map { docno: String ->
                        val searchHit = IndexBackedSearchHit(targetIndex)
                        searchHit.docno = docno
                        searchHit
                    }
    )
    val nonRelDocs = SearchHits(
            queries
                    .map { query -> qrels.getNonRelDocs(query.title) }
                    .reduce { collected, thisQueryNonRelDocs -> collected union thisQueryNonRelDocs }
                    .minus(relDocs.hits().map { it?.docno })
                    .map { docno ->
                        val searchHit = IndexBackedSearchHit(targetIndex)
                        searchHit.docno = docno
                        searchHit
                    }
    )
    val anyDocs = SearchHits(
            queries
                    .map { query -> targetIndex.runQuery(query, 1000) }
                    .fold(HashSet<String>(), { acc, searchHits -> acc.addAll(searchHits.hits().map { it.docno }); acc })
                    .minus(relDocs.hits().map { it?.docno } union nonRelDocs.hits().map { it?.docno })
                    .map { docno ->
                        val searchHit = IndexBackedSearchHit(targetIndex)
                        searchHit.docno = docno
                        searchHit
                    }
    )

    val numberOfEachJudged = (sampleSize * 0.3).toInt()
    val numberOfPool = sampleSize - (numberOfEachJudged * 2)

    val sampledRelDocs = sampleDocuments(numberOfEachJudged, relDocs)
    val sampledNonRelDocs = sampleDocuments(numberOfEachJudged, nonRelDocs)
    val sampledPoolDocs = sampleDocuments(numberOfPool, anyDocs)

    val sampledDocs = sampledRelDocs union sampledNonRelDocs union sampledPoolDocs

    sampledDocs.forEach { doc ->
        val sampledTerms = HashSet<String>()

        // Sample terms from original document
        removeNumbers(doc.featureVector)
        sampledTerms.addAll(sampleTerms(20, doc.featureVector, stopper))

        val expansionRM1 = expansionRM1Builder.buildRelevanceModel(
                documentExpander.createDocumentPseudoQuery(doc),
                documentExpander.expandDocument(doc, 10),
                stopper
        )
        removeNumbers(expansionRM1)
        sampledTerms.addAll(sampleTerms(20, expansionRM1, stopper, exclude = sampledTerms))

        // Include query terms
        val judgedQueries = queries.filter { query -> qrels.contains(query.title, doc.docno) }
        judgedQueries.forEach { query ->
            query.applyStopper(stopper)
            removeNumbers(query.featureVector)
        }
        sampledTerms.addAll(judgedQueries.map { query -> query.featureVector.features }.fold(HashSet(), { acc, terms -> acc.addAll(terms); acc }))

        // Sample terms from RM1
        val rm1Terms = queries
                .filter { query -> qrels.isRel(query.title, doc.docno) }
                .map { relevantQuery ->
                    targetRM1Builder.buildRelevanceModel(relevantQuery, targetIndex.runQuery(relevantQuery, 20), stopper)
                }
                .forEach { rm1 -> removeNumbers(rm1); sampledTerms.addAll(sampleTerms(5, rm1, stopper, exclude = sampledTerms)) }

        println("${doc.docno},${sampledTerms.joinToString(",")}")
    }
}

private fun removeNumbers(vector: FeatureVector) {
    vector.features.filter { term -> StringUtils.isNumeric(term) }.forEach { term -> vector.removeTerm(term) }
}
