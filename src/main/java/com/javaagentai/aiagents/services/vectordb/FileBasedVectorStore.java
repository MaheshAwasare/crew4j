package com.javaagentai.aiagents.services.vectordb;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Author: Mahesh Awasare
 */
public class FileBasedVectorStore implements VectorStore {

    private final String filePath;
    private final ObjectMapper objectMapper;
    // Use CopyOnWriteArrayList for thread-safe iteration during queries,
    // but synchronize writes (upsert, delete, clearAll, loadFile, saveFile)
    private final List<StoredVector> vectorStore;
    private final Object fileLock = new Object(); // Lock for synchronizing file access and vectorStore modifications

    // Private inner record for storing vector data
    private record StoredVector(
            String id,
            List<Double> vector,
            Map<String, Object> metadata,
            String textContent
    ) {
        // Compact constructor for validation if needed
        StoredVector {
            Objects.requireNonNull(id, "StoredVector ID cannot be null.");
            Objects.requireNonNull(vector, "StoredVector vector cannot be null.");
            Objects.requireNonNull(metadata, "StoredVector metadata cannot be null.");
            // textContent can be null
        }
    }

    public FileBasedVectorStore(String filePath) {
        this.filePath = Objects.requireNonNull(filePath, "File path cannot be null.");
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT); // For pretty JSON
        this.vectorStore = new CopyOnWriteArrayList<>(); // Thread-safe for reads, writes need explicit sync
        loadFile();
    }

    private void loadFile() {
        synchronized (fileLock) {
            try {
                File file = new File(filePath);
                if (file.exists() && file.length() > 0) { // Check if file is not empty
                    List<StoredVector> loadedVectors = objectMapper.readValue(file, new TypeReference<List<StoredVector>>() {
                    });
                    // Replace current content with loaded content
                    this.vectorStore.clear();
                    this.vectorStore.addAll(loadedVectors);
                    System.out.println("FileBasedVectorStore: Loaded " + loadedVectors.size() + " vectors from " + filePath);
                } else {
                    System.out.println("FileBasedVectorStore: File not found or empty, starting with an empty store: " + filePath);
                    // Ensure an empty file is created if it doesn't exist, so first save works.
                    // saveFile(); // This would write an empty list. Let's do this on first upsert or clear.
                }
            } catch (IOException e) {
                System.err.println("FileBasedVectorStore: Error loading file " + filePath + ". Starting with an empty store. Error: " + e.getMessage());
                this.vectorStore.clear(); // Ensure store is empty on error
            }
        }
    }

    private void saveFile() {
        synchronized (fileLock) {
            try {
                // Create a temporary list for serialization to avoid issues with CopyOnWriteArrayList's iterator
                List<StoredVector> currentVectors = new ArrayList<>(this.vectorStore);
                objectMapper.writeValue(new File(filePath), currentVectors);
                // System.out.println("FileBasedVectorStore: Saved " + currentVectors.size() + " vectors to " + filePath);
            } catch (IOException e) {
                System.err.println("FileBasedVectorStore: Error saving file " + filePath + ". Error: " + e.getMessage());
            }
        }
    }

    private double cosineSimilarity(List<Double> v1, List<Double> v2) {
        if (v1 == null || v2 == null || v1.size() != v2.size() || v1.isEmpty()) {
            // Consider logging this or throwing IllegalArgumentException if strictness is required
            // For now, returning 0 for invalid inputs or different dimensions.
            return 0.0;
        }

        double dotProduct = 0.0;
        double normV1 = 0.0;
        double normV2 = 0.0;
        for (int i = 0; i < v1.size(); i++) {
            dotProduct += v1.get(i) * v2.get(i);
            normV1 += Math.pow(v1.get(i), 2);
            normV2 += Math.pow(v2.get(i), 2);
        }

        if (normV1 == 0 || normV2 == 0) {
            return 0.0; // Avoid division by zero if one vector is all zeros
        }

        return dotProduct / (Math.sqrt(normV1) * Math.sqrt(normV2));
    }

    @Override
    public CompletableFuture<Void> upsert(List<String> ids, List<List<Double>> vectors,
                                          List<Map<String, Object>> metadataList, List<String> textContents) {
        if (ids.size() != vectors.size() ||
                (metadataList != null && ids.size() != metadataList.size()) ||
                (textContents != null && ids.size() != textContents.size())) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Input lists must have the same size."));
        }

        synchronized (fileLock) {
            List<StoredVector> newStoredVectors = new ArrayList<>();
            for (int i = 0; i < ids.size(); i++) {
                newStoredVectors.add(new StoredVector(
                        ids.get(i),
                        vectors.get(i),
                        metadataList != null ? metadataList.get(i) : Collections.emptyMap(),
                        textContents != null ? textContents.get(i) : null
                ));
            }

            // Perform upsert: remove existing by ID, then add new/updated
            List<String> newIds = newStoredVectors.stream().map(StoredVector::id).collect(Collectors.toList());
            this.vectorStore.removeIf(sv -> newIds.contains(sv.id()));
            this.vectorStore.addAll(newStoredVectors);
            saveFile();
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<List<Document>> query(List<Double> queryVector, int topK, Map<String, Object> filter) {
        List<StoredVectorWithScore> scoredVectors = new ArrayList<>();

        // Iteration is thread-safe with CopyOnWriteArrayList
        for (StoredVector storedVec : this.vectorStore) {
            // Basic metadata filtering
            if (filter != null && !filter.isEmpty()) {
                boolean matchesFilter = filter.entrySet().stream()
                        .allMatch(entry -> entry.getValue().equals(storedVec.metadata().get(entry.getKey())));
                if (!matchesFilter) {
                    continue; // Skip if filter doesn't match
                }
            }

            double similarity = cosineSimilarity(queryVector, storedVec.vector());
            scoredVectors.add(new StoredVectorWithScore(storedVec, similarity));
        }

        // Sort by score descending
        scoredVectors.sort(Comparator.comparingDouble(StoredVectorWithScore::score).reversed());

        List<Document> results = scoredVectors.stream()
                .limit(topK)
                .map(svs -> new Document(svs.storedVector().id(), svs.score(), svs.storedVector().metadata(), svs.storedVector().textContent()))
                .collect(Collectors.toList());

        return CompletableFuture.completedFuture(results);
    }

    // Helper record for sorting with scores
    private record StoredVectorWithScore(StoredVector storedVector, double score) {
    }


    @Override
    public CompletableFuture<Void> delete(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        synchronized (fileLock) {
            this.vectorStore.removeIf(sv -> ids.contains(sv.id()));
            saveFile();
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> clearAll() {
        synchronized (fileLock) {
            this.vectorStore.clear();
            saveFile(); // This will write an empty list to the file
        }
        return CompletableFuture.completedFuture(null);
    }
}
