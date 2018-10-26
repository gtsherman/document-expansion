package org.retrievable.documentExpansion.scripts

import edu.gslis.docscoring.support.IndexBackedCollectionStats
import edu.gslis.indexes.IndexWrapperIndriImpl
import edu.gslis.queries.GQueriesFactory
import edu.gslis.scoring.expansion.StandardRM1Builder
import edu.gslis.utils.Stopper
import org.apache.commons.configuration.PropertiesConfiguration


fun main(args: Array<String>) {
    val config = PropertiesConfiguration(args[0])

    val index = IndexWrapperIndriImpl(config.getString("target-index"))
    val queries = GQueriesFactory.getGQueries(config.getString("queries"))
    val stopper = Stopper(config.getString("stoplist"))

    val collectionStats = IndexBackedCollectionStats()
    collectionStats.setStatSource(index)
    val rm1Builder = StandardRM1Builder(10, 10, collectionStats)

    queries.forEach { query ->
        val rm1 = rm1Builder.buildRelevanceModel(query, index.runQuery(query, 10), stopper)
        rm1.forEach { term ->
            println("${query.title},$term,${rm1.getFeatureWeight(term)}")
        }
    }
}