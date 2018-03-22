package org.retrievable.documentExpansion.analysis

import edu.gslis.eval.Qrels
import edu.gslis.evaluation.evaluators.MAPEvaluator
import edu.gslis.evaluation.evaluators.SetEvaluator
import edu.gslis.queries.GQuery
import edu.gslis.scoring.DocScorer
import edu.gslis.searchhits.SearchHit
import edu.gslis.searchhits.SearchHits
import edu.gslis.textrepresentation.FeatureVector
import edu.gslis.utils.Stopper
import org.retrievable.documentExpansion.data.stemText
import org.retrievable.document_expansion.expansion.DocumentExpander
import org.retrievable.document_expansion.lms.LanguageModelEstimator
import org.retrievable.document_expansion.scoring.ExpansionDocScorer


private data class ScoredItem(val id: String, val score: Double)

private fun pseudoHits(items: Collection<ScoredItem>) : SearchHits {
    return SearchHits(items.map { item -> val hit = SearchHit(); hit.docno = item.id; hit.score = item.score; hit })
}

fun probabilityChange(
        topicTerms: Collection<String>,
        originalDocScorer: DocScorer,
        expansionDocScorer: ExpansionDocScorer,
        document: SearchHit) : Double {
    return topicTerms.toSet().map { term ->
        expansionDocScorer.scoreTerm(term, document) - originalDocScorer.scoreTerm(term, document)
    }.sum()
}

fun totalProbability(topicTerms: Collection<String>, scorer: DocScorer, document: SearchHit) : Double {
    return topicTerms.toSet().map { scorer.scoreTerm(it, document) }.sum()
}

fun changeInAveragePrecision(topicTerms: Collection<String>,
                             document: SearchHit,
                             originalDocScorer: DocScorer,
                             expansionDocScorer: ExpansionDocScorer,
                             numTerms: Int) : Double {
    return topicTermsAveragePrecision(topicTerms, document, expansionDocScorer, numTerms) -
            topicTermsAveragePrecision(topicTerms, document, originalDocScorer, numTerms)
}

fun topicTermsAveragePrecision(topicTerms: Collection<String>, document: SearchHit, scorer: DocScorer, numTerms: Int, stopper: Stopper = Stopper()) : Double {
    // Estimate original LM
    val lm = if (scorer is ExpansionDocScorer) {
        LanguageModelEstimator.expansionLanguageModel(document, scorer)
    } else {
        LanguageModelEstimator.languageModel(document, scorer)
    }
    lm.applyStopper(stopper)
    lm.clip(numTerms)

    // Create pseudo-qrels with topic terms as relevant
    val qrels = Qrels()
    topicTerms.forEach { term -> qrels.addQrel(document.docno, term) }

    val lmPseudoHits = pseudoHits(lm.features.map { ScoredItem(it, lm.getFeatureWeight(it)) })

    return MAPEvaluator().averagePrecision(document.docno, lmPseudoHits, qrels)
}

fun pseudoQueryTermRecall(topicTerms: Collection<String>, document: SearchHit, documentExpander: DocumentExpander) : Double {
    // Set terms as relevant in pseudo-qrels
    val qrels = Qrels()
    topicTerms.forEach { term -> qrels.addQrel(document.docno, term) }

    // Get document pseudo-query
    val pseudoQuery = documentExpander.createDocumentPseudoQuery(document)

    // Use fake SearchHits representing the terms
    val termHits = pseudoHits(pseudoQuery.featureVector.features.map { ScoredItem(it, pseudoQuery.featureVector.getFeatureWeight(it)) })

    return SetEvaluator().recall(document.docno, termHits, qrels)
}

fun pseudoQueryTermJaccard(topicTerms: Collection<String>, document: SearchHit, documentExpander: DocumentExpander) : Double {
    // Get document pseudo-query
    val pseudoQuery = documentExpander.createDocumentPseudoQuery(document)
    return SetEvaluator().jaccardSimilarity(topicTerms.toSet(), pseudoQuery.featureVector.features)
}

fun pseudoQueryVsTopicTermsResultsRecall(topicTerms: Collection<String>, document: SearchHit, documentExpander: DocumentExpander, numDocs: Int) : Double {
    val termSet = topicTerms.toSet()

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

fun pseudoQueryVsTopicTermsResultsJaccard(topicTerms: Collection<String>, document: SearchHit, documentExpander: DocumentExpander, numDocs: Int) : Double {
    val termSet = topicTerms.toSet()

    // Convert topic terms to query
    val topicTermsQuery = GQuery()
    topicTermsQuery.title = "topicTerms"
    topicTermsQuery.featureVector = FeatureVector(null)
    termSet.forEach { term -> topicTermsQuery.featureVector.addTerm(term) }

    // Get results lists
    val topicTermsResults = documentExpander.index.runQuery(topicTermsQuery, numDocs)
    val pseudoQueryResults = documentExpander.expandDocument(document, numDocs)

    return SetEvaluator().jaccardSimilarity(
            topicTermsResults.hits().map { it.docno }.toSet(),
            pseudoQueryResults.hits().map { it.docno }.toSet())
}

fun queryTopicTermSimilarity(topicTerms: Collection<String>, query: GQuery, stopper: Stopper = Stopper(), stem: Boolean = false) : Double {
    val queryVector = query.featureVector.deepCopy()
    queryVector.applyStopper(stopper)

    val topicTermSet = if (stem) stemText(topicTerms.toSet()) else topicTerms.toSet()
    val queryTermSet = if (stem) stemText(queryVector.features) else queryVector.features

    return SetEvaluator().jaccardSimilarity(topicTermSet, queryTermSet)
}