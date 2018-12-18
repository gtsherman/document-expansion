package org.retrievable.document_expansion.expansion;

import edu.gslis.indexes.IndexWrapper;
import edu.gslis.queries.GQuery;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.textrepresentation.FeatureVector;
import edu.gslis.utils.Stopper;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class DocumentExpanderWithPremadePseudoQueries extends DocumentExpander {
    private Map<String, Map<String, Double>> pseudoQueries = new HashMap<>();

    public DocumentExpanderWithPremadePseudoQueries(IndexWrapper index, int numTerms, Stopper stopper) {
        super(index, numTerms, stopper);
    }

    public void readPremadePseudoQueries(String path) {
        try {
            Scanner scanner = new Scanner(new File(path));
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                String[] parts = line.split(",");
                String docno = parts[0];
                String term = parts[1];
                double weight = Double.parseDouble(parts[2]);
                if (!pseudoQueries.containsKey(docno)) {
                    pseudoQueries.put(docno, new HashMap<>());
                }
                pseudoQueries.get(docno).put(term, weight);
            }
            scanner.close();
        } catch (FileNotFoundException e) {
            System.err.println(path + " could not be found. Your program is probably about to crash.");
        }
    }

    @Override
    public GQuery createDocumentPseudoQuery(SearchHit document) {
        FeatureVector vector = new FeatureVector(this.stopper);
        Map<String, Double> pseudoVector = pseudoQueries.get(document.getDocno());
        for (String term : pseudoVector.keySet()) {
            vector.setTerm(term, pseudoVector.get(term));
        }

        GQuery pseudoQuery = new GQuery();
        pseudoQuery.setFeatureVector(vector);

        return pseudoQuery;
    }
}
