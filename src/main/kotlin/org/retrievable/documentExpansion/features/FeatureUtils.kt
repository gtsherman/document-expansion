package org.retrievable.documentExpansion.data

import org.apache.lucene.analysis.CharArraySet
import org.apache.lucene.analysis.en.PorterStemFilter
import org.apache.lucene.analysis.snowball.SnowballFilter
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.tartarus.snowball.ext.EnglishStemmer

fun stemText(terms: Set<String>) : Set<String> {
    return stemText(terms.joinToString(" "))
}

fun stemText(text: String) : Set<String> {
    val tokens = HashSet<String>()

    val tokenStream = PorterStemFilter(StandardAnalyzer(CharArraySet(0, true)).tokenStream(null, text))
    //val tokenStream = SnowballFilter(StandardAnalyzer(CharArraySet(0, true)).tokenStream(null, text), EnglishStemmer())
    //val tokenStream = EnglishMinimalStemFilter(StandardAnalyzer().tokenStream(null, docVector.features.joinToString(" ")))
    val characterRepresentation = tokenStream.addAttribute(CharTermAttribute::class.java)
    tokenStream.reset()

    while (tokenStream.incrementToken()) {
        tokens.add(characterRepresentation.toString())
    }

    return tokens
}