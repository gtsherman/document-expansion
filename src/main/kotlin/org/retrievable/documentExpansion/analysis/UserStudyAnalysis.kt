package org.retrievable.documentExpansion.analysis

import edu.gslis.eval.Qrels
import edu.gslis.evaluation.evaluators.SetEvaluator
import edu.gslis.queries.GQuery
import edu.gslis.scoring.DocScorer
import edu.gslis.searchhits.SearchHit
import edu.gslis.searchhits.SearchHits
import edu.gslis.textrepresentation.FeatureVector
import org.retrievable.document_expansion.expansion.DocumentExpander
import org.retrievable.document_expansion.scoring.ExpansionDocScorer


fun probabilityChange(
        topicTerms: Collection<String>,
        originalDocScorer: DocScorer,
        expansionDocScorer: ExpansionDocScorer,
        document: SearchHit) : Double {
    val termSet = HashSet<String>(topicTerms)
    return termSet.map { term ->
        expansionDocScorer.scoreTerm(term, document) - originalDocScorer.scoreTerm(term, document)
    }.sum()
}

fun pseudoQueryTermRecall(topicTerms: Collection<String>, document: SearchHit, documentExpander: DocumentExpander) : Double {
    // Set terms as relevant in pseudo-qrels
    val qrels = Qrels()
    topicTerms.forEach { term -> qrels.addQrel(document.docno, term) }

    // Get document pseudo-query
    val pseudoQuery = documentExpander.createDocumentPseudoQuery(document)

    // Use fake SearchHits representing the terms
    val termHits = pseudoQuery.featureVector.features.map { term ->
        val hit = SearchHit()
        hit.docno = term
        hit.score = pseudoQuery.featureVector.getFeatureWeight(term)
        hit
    }
    val searchHits = SearchHits(termHits)

    return SetEvaluator().recall(document.docno, searchHits, qrels)
}

fun pseudoQueryVsTopicTerms(topicTerms: Collection<String>, document: SearchHit, documentExpander: DocumentExpander, numDocs: Int) : Double {
    val termSet = HashSet<String>(topicTerms)

    // Convert topic terms to query
    val topicTermsQuery = GQuery()
    topicTermsQuery.title = "topicTerms"
    topicTermsQuery.featureVector = FeatureVector(null)
    termSet.forEach { term -> topicTermsQuery.featureVector.addTerm(term) }

    // Get results lists
    val topicTermsResults = documentExpander.index.runQuery(topicTermsQuery, numDocs)
    val pseudoQueryResults = documentExpander.expandDocument(document, numDocs)

    // Create pseudo-qrels using pseudoQueryResults as relevant documents
    val qrels = Qrels()
    pseudoQueryResults.forEach { result -> qrels.addQrel(document.docno, result.docno) }

    return SetEvaluator().recall(document.docno, topicTermsResults, qrels)
}
