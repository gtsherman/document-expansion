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

    val output = docnos.distinct().parallelStream().map { docno ->
        val doc = IndexBackedSearchHit(index)
        doc.docno = docno

        val pseudoQuery = documentExpander.createDocumentPseudoQuery(doc)

        pseudoQuery.featureVector.map { term ->
            val tf = pseudoQuery.featureVector.getFeatureWeight(term)
            val tfLengthNorm = tf / pseudoQuery.featureVector.length
            val idf = Math.log(index.docCount() / index.docFreq(term))
            "$docno,$term,$tf,$tfLengthNorm,$idf,${tf*idf}"
        }.joinToString("\n")
    }.toArray().joinToString("\n")

    println("docno,term,termFreq,termFreqNorm,idf,tfIDF")
    print(output)

}