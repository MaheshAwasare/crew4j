package com.javaagentai.aiagents.memory;

import com.javaagentai.aiagents.services.embedding.EmbeddingClient;
import com.javaagentai.aiagents.services.vectordb.Document;
import com.javaagentai.aiagents.services.vectordb.VectorStore;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Abstract base class for Long-Term Memory implementations that use
 * an EmbeddingClient and a VectorStore.
 */
public abstract class AbstractLongTermMemory implements Memory {

    protected final EmbeddingClient embeddingClient;
    protected final VectorStore vectorStore;
    private static final long DEFAULT_TIMEOUT_SECONDS = 10; // Default timeout for async operations

    public AbstractLongTermMemory(EmbeddingClient embeddingClient, VectorStore vectorStore) {
        this.embeddingClient = Objects.requireNonNull(embeddingClient, "EmbeddingClient cannot be null.");
        this.vectorStore = Objects.requireNonNull(vectorStore, "VectorStore cannot be null.");
    }

    @Override
    public void add(String key, Object value) {
        Objects.requireNonNull(key, "Key cannot be null for LTM add.");
        if (value instanceof String) {
            String textValue = (String) value;
            try {
                CompletableFuture<List<Double>> embeddingFuture = embeddingClient.embed(textValue);
                List<Double> embeddedVector = embeddingFuture.get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS); // Block here

                if (embeddedVector == null || embeddedVector.isEmpty()) {
                    System.err.println("AbstractLongTermMemory: Failed to generate embedding for key: " + key + ". Vector is null or empty.");
                    return;
                }

                Map<String, Object> metadata = Map.of(
                        "original_key", key,
                        "type", "text_chunk",
                        "timestamp", System.currentTimeMillis(),
                        "text_length", textValue.length()
                );
                // Using key as ID in vector store
                CompletableFuture<Void> upsertFuture = vectorStore.upsert(
                        List.of(key), // Using the key as the document ID
                        List.of(embeddedVector),
                        List.of(metadata),
                        List.of(textValue)
                );
                upsertFuture.get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS); // Block here to ensure completion if interface implies sync
                // System.out.println("AbstractLongTermMemory: Added and embedded text for key: " + key);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("AbstractLongTermMemory: Embedding or upsert was interrupted for key: " + key + ". Error: " + e.getMessage());
            } catch (ExecutionException | TimeoutException e) {
                System.err.println("AbstractLongTermMemory: Failed to embed or upsert text for key: " + key + ". Error: " + e.getMessage());
            }
        } else {
            System.err.println("AbstractLongTermMemory: LTM currently only supports string values for embedding. Key: " + key + ", Value type: " + (value != null ? value.getClass().getName() : "null"));
        }
    }

    @Override
    public void add(Map<String, Object> data) {
        if (data == null) return;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (entry.getValue() instanceof String) {
                add(entry.getKey(), entry.getValue()); // Call the single add method
            } else {
                System.err.println("AbstractLongTermMemory: Skipping non-string value for key: " + entry.getKey() + " in batch add.");
            }
        }
    }

    @Override
    public Object get(String key) {
        // This typically translates to fetching a document by its ID if the VectorStore supports it.
        // The current VectorStore interface doesn't have a direct getById method that returns a single Document's textContent.
        // A query with a filter for the ID could be one way, or extending VectorStore.
        System.err.println("AbstractLongTermMemory: get(key) is not directly supported for semantic entries. " +
                "Use search() or ensure keys are document IDs for direct vector store lookup (feature not fully implemented for direct get). Key: " + key);
        // As a placeholder, one might try to search for it, but it's not a true "get by ID".
        // List<Object> results = search(key, 1); // This would search by content, not ID.
        // if (!results.isEmpty() && results.get(0) instanceof String) {
        //     // This is not ideal as it's a semantic search on the key itself.
        // }
        return null;
    }

    @Override
    public List<Object> getAll() {
        System.err.println("AbstractLongTermMemory: getAll() is impractical for large vector stores and is not supported.");
        return Collections.emptyList();
    }

    @Override
    public List<Object> search(String query, int topK) {
        Objects.requireNonNull(query, "Query cannot be null for LTM search.");
        if (topK <= 0) return Collections.emptyList();

        try {
            CompletableFuture<List<Double>> queryVectorFuture = embeddingClient.embed(query);
            List<Double> queryVector = queryVectorFuture.get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS); // Block here

            if (queryVector == null || queryVector.isEmpty()) {
                System.err.println("AbstractLongTermMemory: Failed to generate embedding for query: " + query + ". Vector is null or empty.");
                return Collections.emptyList();
            }

            // System.out.println("AbstractLongTermMemory: Searching with query: " + query);
            CompletableFuture<List<Document>> searchResultsFuture = vectorStore.query(queryVector, topK, null); // No filter for now
            List<Document> documents = searchResultsFuture.get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS); // Block here

            return documents.stream()
                    .map(Document::textContent) // Extract text content
                    .filter(Objects::nonNull)   // Filter out any null text content
                    .collect(Collectors.toList());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("AbstractLongTermMemory: Search was interrupted for query: " + query + ". Error: " + e.getMessage());
        } catch (ExecutionException | TimeoutException e) {
            System.err.println("AbstractLongTermMemory: Failed to search for query: " + query + ". Error: " + e.getMessage());
        }
        return Collections.emptyList();
    }

    @Override
    public void clear() {
        // System.out.println("AbstractLongTermMemory: Clearing all entries from the vector store.");
        try {
            vectorStore.clearAll().get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS); // Block here
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("AbstractLongTermMemory: Clearing vector store was interrupted. Error: " + e.getMessage());
        } catch (ExecutionException | TimeoutException e) {
            System.err.println("AbstractLongTermMemory: Failed to clear vector store. Error: " + e.getMessage());
        }
    }
}
