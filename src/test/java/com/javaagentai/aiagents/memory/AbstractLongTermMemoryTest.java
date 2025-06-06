package com.javaagentai.aiagents.memory;

import com.javaagentai.aiagents.services.embedding.EmbeddingClient;
import com.javaagentai.aiagents.services.embedding.MockEmbeddingClient;
import com.javaagentai.aiagents.services.vectordb.FileBasedVectorStore;
import com.javaagentai.aiagents.services.vectordb.VectorStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map; // For add(Map)

import static org.junit.jupiter.api.Assertions.*;

/** 
 * Author: Mahesh Awasare 
 */
public class AbstractLongTermMemoryTest {

    @TempDir
    Path tempDir;

    private EmbeddingClient mockEmbeddingClient;
    private VectorStore fileBasedVectorStore;
    private TestLongTermMemory memory;
    private String storeFilePath;

    // Concrete class for testing AbstractLongTermMemory
    static class TestLongTermMemory extends AbstractLongTermMemory {
        public TestLongTermMemory(EmbeddingClient ec, VectorStore vs) {
            super(ec, vs);
        }
    }

    @BeforeEach
    void setUp() {
        mockEmbeddingClient = new MockEmbeddingClient(4); // Using a small, fixed dimension for predictability
        storeFilePath = tempDir.resolve("test_ltm_vector_store.json").toString();
        fileBasedVectorStore = new FileBasedVectorStore(storeFilePath);
        memory = new TestLongTermMemory(mockEmbeddingClient, fileBasedVectorStore);
    }
    
    @AfterEach
    void tearDown() throws IOException {
        // Ensure the vector store file is cleared or deleted if necessary,
        // though @TempDir should handle cleanup of the directory.
        // If tests need pristine file state for each run and rely on constructor loading,
        // deleting the file might be an option, but setUp already creates a new instance.
    }


    @Test
    void testAddAndSearch() {
        String textCats = "This is a test document about cats and felines.";
        String textDogs = "Another document, this one is about dogs and canines.";
        String textBirds = "Birds fly high in the sky.";

        memory.add("cat_doc_id", textCats);
        memory.add("dog_doc_id", textDogs);
        memory.add("bird_doc_id", textBirds);

        // MockEmbeddingClient generates vectors based on text length, hashcode, and char values.
        // "felines" should be closer to "cats and felines" than to "dogs and canines"
        // if the mock vectors reflect this difference sufficiently.
        
        List<Object> resultsCats = memory.search("felines", 1);
        assertNotNull(resultsCats);
        assertFalse(resultsCats.isEmpty(), "Search for 'felines' should return results.");
        assertEquals(1, resultsCats.size());
        assertTrue(resultsCats.contains(textCats), "Search for 'felines' should find the cat document. Found: " + resultsCats.get(0));

        List<Object> resultsDogs = memory.search("canines", 1);
        assertNotNull(resultsDogs);
        assertFalse(resultsDogs.isEmpty(), "Search for 'canines' should return results.");
        assertEquals(1, resultsDogs.size());
        assertTrue(resultsDogs.contains(textDogs), "Search for 'canines' should find the dog document. Found: " + resultsDogs.get(0));

        // Test with a query that might be closer to one than others based on simple vector logic
        List<Object> resultsSky = memory.search("sky", 1);
        assertNotNull(resultsSky);
        assertFalse(resultsSky.isEmpty());
        assertEquals(1, resultsSky.size());
        assertTrue(resultsSky.contains(textBirds));
    }
    
    @Test
    void testAddMapAndSearch() {
        String textCars = "Information about fast cars and automobiles.";
        String textBikes = "Details on mountain bikes and cycling.";

        memory.add(Map.of(
            "car_info_id", textCars,
            "bike_info_id", textBikes
        ));

        List<Object> carResults = memory.search("automobiles", 1);
        assertNotNull(carResults);
        assertEquals(1, carResults.size());
        assertTrue(carResults.contains(textCars));
        
        List<Object> bikeResults = memory.search("cycling", 1);
        assertNotNull(bikeResults);
        assertEquals(1, bikeResults.size());
        assertTrue(bikeResults.contains(textBikes));
    }


    @Test
    void testAddNonStringValue() {
        String key = "integer_doc_id";
        Integer nonStringValue = 12345;

        // This should log an error but not throw an exception as per AbstractLongTermMemory's current design.
        assertDoesNotThrow(() -> memory.add(key, nonStringValue));

        // Attempt to search for it. It shouldn't be found via semantic search as it wasn't embedded.
        List<Object> results = memory.search(String.valueOf(nonStringValue), 1);
        assertTrue(results.isEmpty(), "Searching for a non-string value that wasn't embedded should yield no results.");
        
        // Also, the get method is not supported for semantic entries
        assertNull(memory.get(key), "Get method should return null for entries not directly retrievable by ID in this LTM setup.");
    }

    @Test
    void testClear() throws IOException {
        memory.add("item_to_clear_id", "This item will be cleared from memory.");
        
        List<Object> resultsBeforeClear = memory.search("cleared from memory", 1);
        assertFalse(resultsBeforeClear.isEmpty(), "Item should be found before clear.");
        assertEquals("This item will be cleared from memory.", resultsBeforeClear.get(0));

        memory.clear();

        List<Object> resultsAfterClear = memory.search("cleared from memory", 1);
        assertTrue(resultsAfterClear.isEmpty(), "Item should not be found after clear.");

        // Verify the underlying FileBasedVectorStore's file is now empty or represents an empty store
        File file = new File(storeFilePath);
        assertTrue(file.exists(), "Vector store file should still exist.");
        String content = new String(java.nio.file.Files.readAllBytes(file.toPath()));
        assertEquals("[]", content.trim(), "Vector store file should contain an empty JSON array after clear.");
    }
    
    @Test
    void testSearchWithEmptyQuery() {
        memory.add("doc1", "Some content");
        List<Object> results = memory.search("", 1); // Empty query
        assertTrue(results.isEmpty(), "Search with empty query should return no results.");
    }

    @Test
    void testSearchWithNullQuery() {
        memory.add("doc1", "Some content");
        // AbstractLongTermMemory throws NullPointerException if query is null due to Objects.requireNonNull
        assertThrows(NullPointerException.class, () -> {
            memory.search(null, 1);
        });
    }
    
    @Test
    void testGetAndGetAllAreNotSupported() {
        memory.add("some_key", "some_value");
        assertNull(memory.get("some_key"), "Get is not supported and should return null.");
        assertTrue(memory.getAll().isEmpty(), "GetAll is not supported and should return an empty list.");
    }
}
