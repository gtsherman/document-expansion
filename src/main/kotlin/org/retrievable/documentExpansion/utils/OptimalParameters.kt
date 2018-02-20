package org.retrievable.documentExpansion.utils

import java.io.File
import java.io.FileNotFoundException
import java.util.*


class OptimalParameters(private val paramsFile: File, private val queryName: String) {

    var origWeight = 1.0
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
                    origWeight = paramKeyVals[0].split(":")[1].toDouble()
                    numDocs = paramKeyVals[1].split(":")[1].toInt()
                    numTerms = paramKeyVals[2].split(":")[1].toInt()
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