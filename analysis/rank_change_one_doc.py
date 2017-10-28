import sys

from ir_eval import BatchSearchResults, Qrels


def only_nonrel_docs(batch_search_results, query, qrels, judged_only=False, keep=()):
    nonrel_results = []
    for search_result in batch_search_results.results[query]:
        # if it's a nonrel doc or if it's in the list of docs to keep
        if not qrels.relevant(query, search_result.docno) or search_result.docno in keep:
            if judged_only and not qrels.judged(query, search_result.docno):  # if not judged but we only want judged
                continue  # skip it
            nonrel_results.append(search_result.docno)
    return nonrel_results


def main():
    baseline_file = sys.argv[1]
    test_file = sys.argv[2]
    qrels_file = sys.argv[3]

    baseline = BatchSearchResults()
    baseline.read(baseline_file)

    test = BatchSearchResults()
    test.read(test_file)

    qrels = Qrels()
    qrels.read(qrels_file)

    for query in qrels.qrels:
        for doc in qrels.relevant_documents(query):
            try:
                baseline_rank = only_nonrel_docs(baseline, query, qrels, judged_only=True, keep=(doc)).index(doc) + 1
            except ValueError:
                baseline_rank = -1

            try:
                test_rank = only_nonrel_docs(test, query, qrels, judged_only=True, keep=(doc)).index(doc) + 1
            except ValueError:
                test_rank = -1

            print(query, doc, baseline_rank, test_rank, baseline_rank < test_rank)

main()
