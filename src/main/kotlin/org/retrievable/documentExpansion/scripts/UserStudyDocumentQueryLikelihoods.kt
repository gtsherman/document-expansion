package org.retrievable.documentExpansion.scripts

import edu.gslis.docscoring.support.IndexBackedCollectionStats
import edu.gslis.indexes.IndexWrapperIndriImpl
import edu.gslis.scoring.DirichletDocScorer
import edu.gslis.scoring.InterpolatedDocScorer
import edu.gslis.scoring.queryscoring.QueryLikelihoodQueryScorer
import edu.gslis.searchhits.IndexBackedSearchHit
import edu.gslis.utils.Stopper
import org.apache.commons.configuration.PropertiesConfiguration
import org.retrievable.documentExpansion.main.CollectionSwitch
import org.retrievable.documentExpansion.utils.OptimalParameters
import org.retrievable.document_expansion.expansion.DocumentExpander
import org.retrievable.document_expansion.scoring.ExpansionDocScorer
import java.io.File


fun main(args: Array<String>) {
    // Load config
    val config = PropertiesConfiguration(args[0])
    val annotatedDocsfile = args[1]

    // Load resources from config
    val expansionIndex = IndexWrapperIndriImpl(config.getString("expansion-index"))
    val indexesDir = config.getString("indexes-dir")
    val queriesDir = config.getString("queries-dir")
    val qrelsDir = config.getString("qrels-dir")
    val stopper = Stopper(config.getString("stoplist"))

    val switch = CollectionSwitch(indexesDir, queriesDir, qrelsDir)

    val dirichletDocScorers = HashMap<String, DirichletDocScorer>()
    val dirichletQueryScorers = HashMap<String, QueryLikelihoodQueryScorer>()

    val expansionDocScorers = HashMap<String, ExpansionDocScorer>()
    val expansionQueryScorers = HashMap<String, QueryLikelihoodQueryScorer>()

    File(annotatedDocsfile).forEachLine { line ->
        // Get data
        val annotated = line.trim().split(",")

        val index = annotated[2]

        val doc = IndexBackedSearchHit(switch.index(index))
        doc.docno = annotated[0]

        val query = switch.queries(index).getNamedQuery(annotated[1])
        query.applyStopper(stopper)

        // Score document
        val baselineScore = dirichletQueryScorers.getOrPut(index) {
            QueryLikelihoodQueryScorer(
                    dirichletDocScorers.getOrPut(index) {
                        val collectionStats = IndexBackedCollectionStats()
                        collectionStats.setStatSource(switch.index(index))
                        DirichletDocScorer(collectionStats)
                    }
            )
        }.scoreQuery(query, doc)

        val expandedScore = expansionQueryScorers.getOrPut(index) {
            val optimalParams = OptimalParameters(File("/home/gsherma2/doc-exp/out/$index/wiki/optimal_perq.map"), query.title)

            val dirichletDocScorer = dirichletDocScorers[index]
            val expansionDocScorer = expansionDocScorers.getOrPut(index) {
                ExpansionDocScorer(
                        DocumentExpander(expansionIndex, optimalParams.numTerms, stopper), optimalParams.numDocs
                )
            }
            QueryLikelihoodQueryScorer(
                    InterpolatedDocScorer(
                            mapOf(
                                    Pair(dirichletDocScorer, optimalParams.origWeight),
                                    Pair(expansionDocScorer, 1-optimalParams.origWeight)
                            )
                    )
            )
        }.scoreQuery(query, doc)

        println("${doc.docno},${query.title},$baselineScore,$expandedScore")
    }
}
