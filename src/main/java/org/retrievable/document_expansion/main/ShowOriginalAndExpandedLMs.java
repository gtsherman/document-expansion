package org.retrievable.document_expansion.main;

import java.io.FileNotFoundException;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.retrievable.document_expansion.DocumentExpander;
import org.retrievable.document_expansion.data.ExpandedDocument;

import edu.gslis.indexes.IndexWrapper;
import edu.gslis.indexes.IndexWrapperIndriImpl;
import edu.gslis.queries.GQueries;
import edu.gslis.queries.GQueriesFactory;
import edu.gslis.queries.GQuery;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.textrepresentation.FeatureVector;
import edu.gslis.utils.Stopper;

public class ShowOriginalAndExpandedLMs {

	public static void main(String[] args) throws FileNotFoundException, ConfigurationException {
		Configuration config = new PropertiesConfiguration(args[0]);

		IndexWrapper targetIndex = new IndexWrapperIndriImpl(config.getString("target-index"));
		IndexWrapper expansionIndex = new IndexWrapperIndriImpl(config.getString("expansion-index"));
		GQueries queries = GQueriesFactory.getGQueries(config.getString("queries"));
		Stopper stopper = new Stopper(config.getString("stoplist"));
		
		stopper.addStopword("one");
		stopper.addStopword("two");
		stopper.addStopword("three");
		stopper.addStopword("four");
		stopper.addStopword("five");
		stopper.addStopword("six");
		stopper.addStopword("seven");
		stopper.addStopword("eight");
		stopper.addStopword("nine");
		stopper.addStopword("ten");
		
		DocumentExpander docExpander = new DocumentExpander(expansionIndex, stopper);
		
		for (GQuery query : queries) {
			System.out.println("*** QUERY: " + query.getText() + " ***");
			SearchHits docs = targetIndex.runQuery(query, 10);
			for (SearchHit doc : docs) {
				ExpandedDocument expanded = docExpander.expandDocument(doc);

				FeatureVector origLM = expanded.originalLanguageModel(targetIndex);
				origLM.applyStopper(stopper);
				FeatureVector expLM = expanded.expansionLanguageModel(expansionIndex);
				expLM.applyStopper(stopper);
				
				int terms = 10;
				origLM.clip(terms);
				expLM.clip(terms);
				
				System.out.println("Original:");
				System.out.println(origLM.toString());
				System.out.println();
				System.out.println("Expanded:");
				System.out.println(expLM.toString());
				System.out.println();
				System.out.println();
			}
		}
	}

}
