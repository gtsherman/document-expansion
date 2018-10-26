package org.retrievable.documentExpansion.scripts

import edu.gslis.indexes.IndexWrapperIndriImpl
import edu.gslis.searchhits.IndexBackedSearchHit
import edu.gslis.utils.Stopper
import org.apache.commons.configuration.PropertiesConfiguration
import org.retrievable.document_expansion.expansion.DocumentExpander
import java.io.File


fun main(args: Array<String>) {
    val config = PropertiesConfiguration(args[0])

    val index = IndexWrapperIndriImpl(config.getString("target-index"))
    val stopper = Stopper(config.getString("stoplist"))

    val documentExpander = DocumentExpander(index, 10, stopper)

    val docnos = File(args[1]).readLines().map { it.trim() }

    docnos.distinct().forEach { docno ->
        val doc = IndexBackedSearchHit(index)
        doc.docno = docno

        val pseudoQuery = documentExpander.createDocumentPseudoQuery(doc)

        pseudoQuery.featureVector.forEach { term ->
            println("$docno,$term,${pseudoQuery.featureVector.getFeatureWeight(term)}")
        }
    }
}