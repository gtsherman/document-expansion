package org.retrievable.documentExpansion.utils

import java.io.File
import java.io.FileNotFoundException
import java.util.*


class OptimalParameters(private val paramsFile: File, private val queryName: String) {

    private val ORIGINAL_WEIGHT = "origW"
    private val EXPANSION_WEIGHT = "expW"
    private val EXPANSION_DOCS = "expDocs"
    private val EXPANSION_TERMS = "expTerms"

    var origWeight = 1.0
    var expWeights = listOf(0.0)
    var numDocs = 5
    var numTerms = 5

    init {
        readOptimalParameters()
    }

    /** Ugliness that basically means read in a file that shows the optimal params determined for a standard expansion run
     *  (i.e. optimal exp. docs and terms) and return those values.
     */
    private fun readOptimalParameters() {
        try {
            val scanner = Scanner(paramsFile)
            while (scanner.hasNextLine()) {
                val line = scanner.nextLine()
                val parts = line.split(" ")
                if (parts[0] == queryName) {
                    val paramKeyVals = parts[1].trim().split(",")
                    origWeight = paramKeyVals.first { it.startsWith(ORIGINAL_WEIGHT) }.split(":").last().toDouble()
                    numDocs = paramKeyVals.first { it.startsWith(EXPANSION_DOCS) }.split(":").last().toInt()
                    numTerms = paramKeyVals.first { it.startsWith(EXPANSION_TERMS) }.split(":").last().toInt()
                    expWeights = paramKeyVals.filter { it.startsWith(EXPANSION_WEIGHT) }.map { it.split(":").last().toDouble() }
                    break
                }
            }
            scanner.close()
        } catch (e: FileNotFoundException) {
            System.err.println("$paramsFile not found")
            System.exit(-1)
        }
    }

}