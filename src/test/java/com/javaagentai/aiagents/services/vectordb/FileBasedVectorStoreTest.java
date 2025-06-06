package com.javaagentai.aiagents.services.vectordb;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

/** 
 * Author: Mahesh Awasare 
 */
public class FileBasedVectorStoreTest {

    @TempDir
    Path tempDir;

    private FileBasedVectorStore vectorStore;
    private String storeFilePath;

    // Private inner record for reading back StoredVector, needs to be accessible for ObjectMapper
    // Or make StoredVector in FileBasedVectorStore public/package-private if not already.
    // For simplicity, if StoredVector is private, we might need to rely on query results for some assertions.
    // Assuming StoredVector is not accessible directly for this test for now.
    // We'll verify persistence by querying a new instance.

    @BeforeEach
    void setUp() {
        storeFilePath = tempDir.resolve("test_vector_store.json").toString();
        vectorStore = new FileBasedVectorStore(storeFilePath);
    }

    private List<Double> vec(double... values) {
        List<Double> list = new java.util.ArrayList<>();
        for (double d : values) {
            list.add(d);
        }
        return list;
    }

    @Test
    void testUpsertAndQuery() throws ExecutionException, InterruptedException {
        List<String> ids = List.of("id1", "id2");
        List<List<Double>> vectors = List.of(vec(1.0, 0.0), vec(0.0, 1.0));
        List<Map<String, Object>> metadata = List.of(Map.of("genre", "sci-fi"), Map.of("genre", "fantasy"));
        List<String> texts = List.of("Sci-fi document", "Fantasy document");

        vectorStore.upsert(ids, vectors, metadata, texts).get();

        // Query for id1
        List<Document> results1 = vectorStore.query(vec(1.0, 0.0), 1, null).get();
        assertEquals(1, results1.size());
        assertEquals("id1", results1.get(0).id());
        assertEquals("Sci-fi document", results1.get(0).textContent());
        assertTrue(results1.get(0).score() > 0.99, "Score should be very high for exact match.");

        // Query for id2 with a slightly off vector
        List<Document> results2 = vectorStore.query(vec(0.1, 0.9), 1, null).get();
        assertEquals(1, results2.size());
        assertEquals("id2", results2.get(0).id());
        assertTrue(results2.get(0).score() > 0.8 && results2.get(0).score() < 1.0, "Score should be high but not perfect.");


        // Query with a completely different vector
        List<Document> results3 = vectorStore.query(vec(-1.0, -1.0), 1, null).get();
        if (!results3.isEmpty()) { // Depending on implementation, might return low score or empty
             // If it returns, score should be low or negative if similarity allows
            assertTrue(results3.get(0).score() < 0.5, "Score should be low for orthogonal/opposite vector.");
        }
    }

    @Test
    void testQueryWithFilter() throws ExecutionException, InterruptedException {
        vectorStore.upsert(
                List.of("id1", "id2", "id3"),
                List.of(vec(1.0, 0.0, 0.0), vec(0.0, 1.0, 0.0), vec(1.0, 0.1, 0.0)),
                List.of(Map.of("type", "A", "year", 2020), Map.of("type", "B", "year", 2021), Map.of("type", "A", "year", 2022)),
                List.of("Doc A1", "Doc B1", "Doc A2")
        ).get();

        // Query with filter for type A
        List<Document> resultsTypeA = vectorStore.query(vec(1.0, 0.0, 0.0), 5, Map.of("type", "A")).get();
        assertEquals(2, resultsTypeA.size());
        assertTrue(resultsTypeA.stream().allMatch(doc -> doc.metadata().get("type").equals("A")));
        // Check if "id1" and "id3" are present (order might vary based on score)
        assertTrue(resultsTypeA.stream().anyMatch(doc -> doc.id().equals("id1")));
        assertTrue(resultsTypeA.stream().anyMatch(doc -> doc.id().equals("id3")));


        // Query with filter for type B
        List<Document> resultsTypeB = vectorStore.query(vec(0.0, 1.0, 0.0), 5, Map.of("type", "B")).get();
        assertEquals(1, resultsTypeB.size());
        assertEquals("id2", resultsTypeB.get(0).id());

        // Query with filter for type A and specific year
        List<Document> resultsTypeAYear = vectorStore.query(vec(1.0, 0.0, 0.0), 5, Map.of("type", "A", "year", 2022)).get();
        assertEquals(1, resultsTypeAYear.size());
        assertEquals("id3", resultsTypeAYear.get(0).id());
        
        // Query with filter that matches nothing
        List<Document> resultsNoMatch = vectorStore.query(vec(1.0, 0.0, 0.0), 5, Map.of("type", "C")).get();
        assertTrue(resultsNoMatch.isEmpty());
    }

    @Test
    void testDelete() throws ExecutionException, InterruptedException {
        vectorStore.upsert(
                List.of("id1-del", "id2-keep"),
                List.of(vec(0.5, 0.5), vec(0.6, 0.4)),
                List.of(Collections.emptyMap(), Collections.emptyMap()),
                List.of("To be deleted", "To be kept")
        ).get();

        List<Document> resultsBeforeDelete = vectorStore.query(vec(0.5, 0.5), 1, null).get();
        assertEquals(1, resultsBeforeDelete.size());
        assertEquals("id1-del", resultsBeforeDelete.get(0).id());

        vectorStore.delete(List.of("id1-del")).get();

        List<Document> resultsAfterDelete = vectorStore.query(vec(0.5, 0.5), 1, null).get();
        // If id2-keep is close enough, it might be returned. The important part is id1-del is gone.
        if (!resultsAfterDelete.isEmpty()) {
            assertNotEquals("id1-del", resultsAfterDelete.get(0).id(), "Deleted document should not be found.");
        }
        
        // More robust check: query for all, ensure id1-del is not there and id2-keep is.
        // FileBasedVectorStore doesn't have a "get all" method.
        // We can try to query with a very generic vector or check file content if StoredVector was accessible.
        // For now, let's query specifically for id2-keep
        List<Document> resultsKeep = vectorStore.query(vec(0.6,0.4),1,null).get();
        assertEquals(1, resultsKeep.size());
        assertEquals("id2-keep", resultsKeep.get(0).id());
    }

    @Test
    void testClearAll() throws ExecutionException, InterruptedException, IOException {
        vectorStore.upsert(
                List.of("id1-clear", "id2-clear"),
                List.of(vec(0.2, 0.8), vec(0.8, 0.2)),
                List.of(Collections.emptyMap(), Collections.emptyMap()),
                List.of("Clear doc 1", "Clear doc 2")
        ).get();

        vectorStore.clearAll().get();

        List<Document> results = vectorStore.query(vec(0.2, 0.8), 1, null).get();
        assertTrue(results.isEmpty(), "Store should be empty after clearAll.");

        // Verify file content is empty list
        File file = new File(storeFilePath);
        assertTrue(file.exists());
        String content = new String(java.nio.file.Files.readAllBytes(file.toPath()));
        assertEquals("[]", content.trim(), "File should contain an empty JSON array after clearAll.");
    }

    @Test
    void testPersistence() throws ExecutionException, InterruptedException, IOException {
        List<String> ids = List.of("persist-id1");
        List<List<Double>> vectors = List.of(vec(0.7, 0.3));
        List<Map<String, Object>> metadata = List.of(Map.of("key", "value"));
        List<String> texts = List.of("Persistent document");

        vectorStore.upsert(ids, vectors, metadata, texts).get(); // Upsert to store1 (vectorStore instance)

        // Create a new instance using the same file path
        FileBasedVectorStore store2 = new FileBasedVectorStore(storeFilePath);
        List<Document> resultsStore2 = store2.query(vec(0.7, 0.3), 1, null).get();

        assertEquals(1, resultsStore2.size());
        assertEquals("persist-id1", resultsStore2.get(0).id());
        assertEquals("Persistent document", resultsStore2.get(0).textContent());
        assertEquals("value", resultsStore2.get(0).metadata().get("key"));
    }

    @Test
    void testUpsertUpdatesExisting() throws ExecutionException, InterruptedException {
        vectorStore.upsert(
            List.of("id-update"),
            List.of(vec(1.0, 0.0)),
            List.of(Map.of("version", 1)),
            List.of("Version 1")
        ).get();

        List<Document> resultsV1 = vectorStore.query(vec(1.0,0.0), 1, null).get();
        assertEquals("Version 1", resultsV1.get(0).textContent());
        assertEquals(1, (Integer)resultsV1.get(0).metadata().get("version"));

        // Now upsert the same ID with new data
         vectorStore.upsert(
            List.of("id-update"),
            List.of(vec(1.0, 0.1)), // slightly different vector
            List.of(Map.of("version", 2)),
            List.of("Version 2")
        ).get();
        
        List<Document> resultsV2 = vectorStore.query(vec(1.0,0.1), 1, null).get();
        assertEquals(1, resultsV2.size());
        assertEquals("id-update", resultsV2.get(0).id());
        assertEquals("Version 2", resultsV2.get(0).textContent());
        assertEquals(2, (Integer)resultsV2.get(0).metadata().get("version"));

        // Ensure old version is gone (by checking file content or ensuring query doesn't find V1 by old vector if distinct enough)
        // This check is slightly harder without direct access to the internal list size or getById.
        // A query for the old vector should ideally not find the updated document if vector changed significantly,
        // or if it does, it should be the V2 content.
        List<Document> resultsOldVec = vectorStore.query(vec(1.0,0.0), 1, null).get();
        if(!resultsOldVec.isEmpty()){ // if the new vector is still the closest match to the old query vector
            assertEquals("Version 2", resultsOldVec.get(0).textContent(), "Querying with old vector should still yield new content if it's the only doc with that ID.");
        }
    }
}
