package com.javaagentai.aiagents.services.vectordb;

import com.javaagentai.aiagents.services.embedding.EmbeddingClient;
import com.javaagentai.aiagents.services.embedding.MockEmbeddingClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS) // Can use PER_METHOD if preferred, adjust setup/teardown
public class ChromaDBVectorStoreIntegrationTest {

    private static final String CHROMA_URL = System.getProperty("CHROMA_URL", "http://localhost:8000");
    private static final String BASE_COLLECTION_NAME = "test_integration_collection_";
    private String currentCollectionName;

    private ChromaDBVectorStore vectorStore;
    private EmbeddingClient mockEmbeddingClient;

    @BeforeEach
    void setUp() {
        // Check if ChromaDB is running - basic check, can be more sophisticated
        try {
            // A simple check if the ChromaDB server is responsive.
            // This is not a foolproof way but can prevent tests from running if server is clearly down.
            // For a real test suite, a health check endpoint of ChromaDB would be better.
            // For now, we'll assume it's running or let tests fail if it's not.
            System.out.println("Attempting to connect to ChromaDB at: " + CHROMA_URL + ". Ensure ChromaDB is running for these integration tests.");
        } catch (Exception e) {
            assumeTrue(false, "ChromaDB not reachable at " + CHROMA_URL + ". Skipping integration tests. Error: " + e.getMessage());
        }

        currentCollectionName = BASE_COLLECTION_NAME + UUID.randomUUID().toString().substring(0, 8);
        mockEmbeddingClient = new MockEmbeddingClient(4); // Dimension matching test vectors
        try {
            vectorStore = new ChromaDBVectorStore(CHROMA_URL, currentCollectionName, mockEmbeddingClient);
            System.out.println("Using ChromaDB collection: " + currentCollectionName);
        } catch (Exception e) {
             // If client fails to initialize (e.g. ChromaDB not running), skip tests.
            System.err.println("Failed to initialize ChromaDBVectorStore for collection " + currentCollectionName + ". Error: " + e.getMessage());
            assumeTrue(false, "Failed to initialize ChromaDBVectorStore. Skipping tests. Is ChromaDB running at " + CHROMA_URL + "?");
        }
    }

    @AfterEach
    void tearDown() throws ExecutionException, InterruptedException {
        if (vectorStore != null) {
            System.out.println("Cleaning up ChromaDB collection: " + currentCollectionName);
            try {
                vectorStore.clearAll().get(10, TimeUnit.SECONDS); // Wait for cleanup
            } catch (Exception e) {
                System.err.println("Error during ChromaDB collection cleanup (" + currentCollectionName + "): " + e.getMessage());
                // Potentially try a direct client delete if store is in bad state, or log and move on.
            }
        }
    }
    
    private List<Double> vec(double... values) {
        List<Double> list = new java.util.ArrayList<>();
        for (double d : values) {
            list.add(d);
        }
        return list;
    }

    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void testUpsertAndQuery() throws ExecutionException, InterruptedException {
        List<String> ids = List.of("chroma-id1", "chroma-id2");
        List<List<Double>> vectors = List.of(vec(0.1, 0.2, 0.3, 0.4), vec(0.5, 0.6, 0.7, 0.8));
        List<Map<String, Object>> metadata = List.of(Map.of("type", "doc"), Map.of("type", "blog"));
        List<String> texts = List.of("ChromaDB document 1", "ChromaDB blog post 2");

        vectorStore.upsert(ids, vectors, metadata, texts).get();

        // Query for id1
        List<Document> results1 = vectorStore.query(vec(0.1, 0.2, 0.3, 0.4), 1, null).get();
        assertEquals(1, results1.size());
        Document doc1 = results1.get(0);
        assertEquals("chroma-id1", doc1.id());
        assertEquals("ChromaDB document 1", doc1.textContent());
        assertEquals("doc", doc1.metadata().get("type"));
        // ChromaDB distances are typically L2 squared. For an exact match, it should be 0.0.
        assertTrue(doc1.score() < 0.0001, "Score (distance) should be near zero for exact match. Was: " + doc1.score());

        // Query for id2 with a slightly different vector
        List<Document> results2 = vectorStore.query(vec(0.5, 0.6, 0.7, 0.75), 1, null).get(); // last element slightly off
        assertEquals(1, results2.size());
        Document doc2 = results2.get(0);
        assertEquals("chroma-id2", doc2.id());
        assertTrue(doc2.score() > 0 && doc2.score() < 0.1, "Score (distance) should be small for similar vector. Was: " + doc2.score());
    }

    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void testQueryWithFilter() throws ExecutionException, InterruptedException {
        vectorStore.upsert(
            List.of("f-id1", "f-id2", "f-id3"),
            List.of(vec(0.1, 0.2, 0.0, 0.0), vec(0.3, 0.4, 0.0, 0.0), vec(0.1, 0.25, 0.0, 0.0)),
            List.of(Map.of("category", "X", "year", 2023), Map.of("category", "Y", "year", 2023), Map.of("category", "X", "year", 2024)),
            List.of("Filter Doc X1", "Filter Doc Y1", "Filter Doc X2")
        ).get();

        // Query with filter for category X
        Map<String, Object> filterX = Map.of("category", "X");
        List<Document> resultsX = vectorStore.query(vec(0.1, 0.2, 0.0, 0.0), 5, filterX).get();
        assertEquals(2, resultsX.size(), "Should find 2 documents with category X.");
        assertTrue(resultsX.stream().allMatch(doc -> doc.metadata().get("category").equals("X")));

        // Query with filter for category Y
        Map<String, Object> filterY = Map.of("category", "Y");
        List<Document> resultsY = vectorStore.query(vec(0.3, 0.4, 0.0, 0.0), 5, filterY).get();
        assertEquals(1, resultsY.size(), "Should find 1 document with category Y.");
        assertEquals("f-id2", resultsY.get(0).id());

        // Query with composite filter
        Map<String, Object> filterX2024 = Map.of("category", "X", "year", 2024);
        List<Document> resultsX2024 = vectorStore.query(vec(0.1, 0.2, 0.0, 0.0), 5, filterX2024).get();
        assertEquals(1, resultsX2024.size(), "Should find 1 document with category X and year 2024.");
        assertEquals("f-id3", resultsX2024.get(0).id());
    }
    
    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void testDelete() throws ExecutionException, InterruptedException {
        String idToDel = "chroma-del-id1";
        vectorStore.upsert(
            List.of(idToDel, "chroma-keep-id2"),
            List.of(vec(0.7, 0.7, 0.7, 0.7), vec(0.8, 0.8, 0.8, 0.8)),
            List.of(Collections.emptyMap(), Collections.emptyMap()),
            List.of("To delete", "To keep")
        ).get();

        // Confirm presence
        List<Document> resultsBefore = vectorStore.query(vec(0.7, 0.7, 0.7, 0.7), 1, null).get();
        assertEquals(1, resultsBefore.size());
        assertEquals(idToDel, resultsBefore.get(0).id());

        vectorStore.delete(List.of(idToDel)).get();

        // Confirm deletion (querying for the deleted vector should not yield the same ID)
        List<Document> resultsAfter = vectorStore.query(vec(0.7, 0.7, 0.7, 0.7), 1, null).get();
        if (!resultsAfter.isEmpty()) {
            assertNotEquals(idToDel, resultsAfter.get(0).id(), "Deleted document should not be found by its vector if other docs are dissimilar.");
        }
        // A more robust way is to try to query by ID if Chroma client supported it directly, or check count.
        // For now, we rely on the specific vector query.
        
        // Confirm "chroma-keep-id2" is still there
        List<Document> resultsKeep = vectorStore.query(vec(0.8, 0.8, 0.8, 0.8), 1, null).get();
        assertEquals(1, resultsKeep.size());
        assertEquals("chroma-keep-id2", resultsKeep.get(0).id());
    }

    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void testUpsertUpdatesExisting() throws ExecutionException, InterruptedException {
        String idUpdate = "chroma-update-id";
        vectorStore.upsert(
            List.of(idUpdate),
            List.of(vec(0.1, 0.1, 0.1, 0.1)),
            List.of(Map.of("version", 1)),
            List.of("Version 1 text")
        ).get();

        List<Document> resultsV1 = vectorStore.query(vec(0.1, 0.1, 0.1, 0.1), 1, null).get();
        assertEquals("Version 1 text", resultsV1.get(0).textContent());
        assertEquals(1, (Integer)resultsV1.get(0).metadata().get("version"));

        // Upsert again with same ID
        vectorStore.upsert(
            List.of(idUpdate),
            List.of(vec(0.2, 0.2, 0.2, 0.2)), // Different vector
            List.of(Map.of("version", 2)),
            List.of("Version 2 text")
        ).get();

        List<Document> resultsV2 = vectorStore.query(vec(0.2, 0.2, 0.2, 0.2), 1, null).get();
        assertEquals(1, resultsV2.size());
        assertEquals(idUpdate, resultsV2.get(0).id());
        assertEquals("Version 2 text", resultsV2.get(0).textContent());
        assertEquals(2, (Integer)resultsV2.get(0).metadata().get("version"));
        assertTrue(resultsV2.get(0).score() < 0.0001, "Score for V2 should be near zero.");
    }

    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void testClearAllExplicit() throws ExecutionException, InterruptedException {
        vectorStore.upsert(
            List.of("clear-id1", "clear-id2"),
            List.of(vec(0.4, 0.4, 0.4, 0.4), vec(0.5, 0.5, 0.5, 0.5)),
            List.of(Collections.emptyMap(), Collections.emptyMap()),
            List.of("Doc to clear 1", "Doc to clear 2")
        ).get();

        vectorStore.clearAll().get(); // This deletes and recreates the collection

        // Attempt to query. The collection should be empty.
        // Querying a non-existent or empty collection might throw exception or return empty.
        // ChromaDB client's query on an empty collection should return empty results.
        List<Document> results = vectorStore.query(vec(0.4, 0.4, 0.4, 0.4), 1, null).get();
        assertTrue(results.isEmpty(), "Store should be empty after clearAll.");

        // Try to add something new to ensure collection is usable
        vectorStore.upsert(List.of("new-id"), List.of(vec(0.6,0.6,0.6,0.6)), List.of(Collections.emptyMap()), List.of("New Doc")).get();
        List<Document> resultsNew = vectorStore.query(vec(0.6,0.6,0.6,0.6), 1, null).get();
        assertEquals(1, resultsNew.size());
        assertEquals("new-id", resultsNew.get(0).id());
    }
}
