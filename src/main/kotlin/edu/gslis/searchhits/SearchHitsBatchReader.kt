package edu.gslis.searchhits

import java.io.File
import java.io.FileNotFoundException


fun readTrecOutput(fileName: String) : SearchHitsBatch {
    val searchHitsBatch = SearchHitsBatch()

    val file: File
    try {
        file = File(fileName)
    } catch (e: FileNotFoundException) {
        System.err.println("Cannot open file $fileName")
        return searchHitsBatch
    }

    val lines = file.readLines()

    var searchHits = SearchHits()
    var currentQuery: String = lines[0].split(' ')[0]
    lines.forEach { line ->
        val data = line.split(' ')

        val query: String = data[0]
        if (query !== currentQuery) {
            searchHitsBatch.setSearchHits(currentQuery, searchHits)
            searchHits = SearchHits()
            currentQuery = query
        }

        val searchHit = SearchHit()
        searchHit.docno = data[2]
        searchHit.score = data[4].toDouble()

        searchHits.add(searchHit)
    }

    return searchHitsBatch
}