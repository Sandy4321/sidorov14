package corpus;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;

import util.Utilities;

public class CorpusManager implements ICorpusManager {
	private Path corpusLocation;
	private String metaFileName = "meta-file.json";
	private String groundTruthFileName = "ground-truth.json";
	private String encoding;
	private String unknownFolder;
	private String language;
	private List<String> authors;
	private List<TextInstance> knownTexts;
	private List<TextInstance> unknownTexts;
	private HashMap<String, String> authorTextMapping;
	
	private Iterator<TextInstance> knownTextIterator;
	private Iterator<TextInstance> unknownTextIterator;

	public CorpusManager(String location) throws IOException {
		corpusLocation = new File(location).toPath();
		File metaFile = new File(corpusLocation.toFile(), metaFileName);
		File groundTruthFile = new File(corpusLocation.toFile(), groundTruthFileName);

		if (!metaFile.exists()) {
			throw new IOException("Could not find meta file '" + metaFile.toString() + "'");
		}

		if (!groundTruthFile.exists()) {
			throw new IOException("Could not find groundTruth file '" + groundTruthFile.toString() + "'");
		}

		JsonObject metadata = null;
		JsonObject groundData = null;
		
		try (InputStream metaInputStream = new FileInputStream(metaFile);
				JsonReader metaReader = Json.createReader(metaInputStream);
				InputStream groundInputStream = new FileInputStream(groundTruthFile);
				JsonReader groundReader = Json.createReader(groundInputStream);) {

			metadata = metaReader.readObject();
			groundData = groundReader.readObject();
		}
		catch (Exception e) {
			throw new IOException("Failed to read JSON: " + e.getMessage());
		}
		
		encoding = metadata.getString("encoding");
		unknownFolder = metadata.getString("folder");
		language = metadata.getString("language");
		
		authors = new ArrayList<String>();
		for(JsonObject author : metadata.getJsonArray("candidate-authors").getValuesAs(JsonObject.class))
		{
			authors.add(author.getString("author-name"));
		}
		
		unknownTexts = new ArrayList<TextInstance>();
		discoverUnknownTexts();
		unknownTextIterator = unknownTexts.iterator();
		
		knownTexts = new ArrayList<TextInstance>();
		discoverKnownTexts();
		knownTextIterator = knownTexts.iterator();
		
		authorTextMapping = new HashMap<String, String>();
		for (JsonObject truth : groundData.getJsonArray("ground-truth").getValuesAs(JsonObject.class))
		{
			authorTextMapping.put(truth.getString("unknown-text"), truth.getString("true-author"));
		}
	}

	private void discoverKnownTexts() {
		assert(authors.size() > 0);
		
		for (String author : authors)
		{
			File authorFolder = new File(corpusLocation.toFile(), author);
			if ((!authorFolder.exists()) || (!authorFolder.isDirectory()))
			{
				System.err.println("Could not open folder " + authorFolder + ", skipping.");
				continue;
			}
			
			List<Path> texts = Utilities.getDirectoryContents(authorFolder);
			for (Path text : texts)
			{
				if (text.toFile().exists())
				{
					TextInstance instance = new TextInstance(author, text.toFile());
					knownTexts.add(instance);
				}
				else
				{
					System.err.println("Could not locate file " + text.toString() + ", skipping.");
					continue;
				}
			}
		}
	}
	
	private void discoverUnknownTexts()
	{
		File unknown = new File(corpusLocation.toFile(), unknownFolder);
		List<Path> texts = Utilities.getDirectoryContents(unknown);
		for (Path unknownText : texts)
		{
			TextInstance instance = new TextInstance(unknownText.toFile().getName(), unknownText.toFile());
			unknownTexts.add(instance);
		}
	}
	
	@Override
	public TextInstance getNextText() {
		if (knownTextIterator.hasNext())
		{
			return knownTextIterator.next();
		}
		else
		{
			return null;
		}
	}

	@Override
	public boolean validateUnknownAttribution(File text, String author) {
		if (authorTextMapping.get(text) == author)
		{
			return true;
		}
		else
		{
			return false;
		}
	}

	@Override
	public List<String> getAllAuthors() {
		return authors;
	}

	@Override
	public int getTextCount() {
		return knownTexts.size();
	}

	public static void main(String[] args) {
		try {
			CorpusManager c = new CorpusManager("Corpus/NEW CORPORA/C10/");
			TextInstance i = c.getNextText();
			System.out.println(i.getTrueAuthor());
			System.out.println(i.getTextSource());
			System.out.println(i.getFullText());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public TextInstance getUnknownText() {
		if (unknownTextIterator.hasNext())
		{
			return unknownTextIterator.next();
		}
		else
		{
			return null;
		} 
	}

	@Override
	public int getUnknownTextCount() {
		return unknownTexts.size();
	}
	
	public HashMap<String, String> getAuthorTextMapping() {
		return authorTextMapping;
	}
}
