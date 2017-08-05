package org.retrievable.document_expansion.main;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;

import org.retrievable.document_expansion.DocumentExpander;
import org.retrievable.document_expansion.data.ExpandedDocument;
import org.retrievable.document_expansion.features.LMFeatures;

import com.google.common.collect.Streams;

import cc.mallet.util.Maths;
import edu.gslis.indexes.IndexWrapper;
import edu.gslis.indexes.IndexWrapperIndriImpl;
import edu.gslis.queries.GQuery;
import edu.gslis.searchhits.IndexBackedSearchHit;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.utils.Stopper;

public class CompareOriginalAndExpandedLMs {

	public static void main(String[] args) throws FileNotFoundException {
		IndexWrapper targetIndex = new IndexWrapperIndriImpl(args[0]);
		IndexWrapper expansionIndex = new IndexWrapperIndriImpl(args[1]);
		Stopper stopper = new Stopper(args[2]);
		String docsFile = args[3];
		
		DocumentExpander docExpander = new DocumentExpander(expansionIndex, stopper);
		
		SearchHits docs = new SearchHits();
		Scanner scanner = new Scanner(new File(docsFile));
		while (scanner.hasNextLine()) {
			String doc = scanner.nextLine().trim();
			SearchHit docHit = new IndexBackedSearchHit(expansionIndex);
			docHit.setDocno(doc);
			docs.add(docHit);
		}
		scanner.close();
		
		Map<String, Double> klDivergences = new ConcurrentHashMap<String, Double>();
		Streams.stream(docs).parallel().forEach(doc -> {
			System.err.println("Working on " + doc.getDocno());

			GQuery p = new GQuery();
			p.setFeatureVector(doc.getFeatureVector());
			p.applyStopper(stopper);
			doc.setFeatureVector(p.getFeatureVector());

			ExpandedDocument expanded = docExpander.expandDocument(doc);
			double kl = LMFeatures.compareLanguageModels(expanded.originalLanguageModel(targetIndex),
					expanded.expansionLanguageModel(expansionIndex),
					Maths::klDivergence);
			klDivergences.put(doc.getDocno(), kl);
		});
		
		klDivergences.keySet().stream().forEach(doc -> System.out.println(doc + ": " + klDivergences.get(doc)));
	}

}
