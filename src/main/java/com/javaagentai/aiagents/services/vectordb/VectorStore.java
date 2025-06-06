package com.javaagentai.aiagents.services.vectordb;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for vector database operations.
 */
public interface VectorStore {

    /**
     * Upserts (inserts or updates) documents into the vector store.
     * All lists must have the same size.
     *
     * @param ids          A list of unique identifiers for the documents.
     * @param vectors      A list of vector embeddings corresponding to the documents.
     * @param metadataList A list of metadata maps for each document. Can be null or contain empty maps.
     * @param textContents A list of original text content for each document. Can be null or contain nulls.
     * @return A CompletableFuture that completes when the upsert operation is finished.
     * @throws IllegalArgumentException if the lists have different sizes.
     */
    CompletableFuture<Void> upsert(List<String> ids,
                                   List<List<Double>> vectors,
                                   List<Map<String, Object>> metadataList,
                                   List<String> textContents);

    /**
     * Queries the vector store for the most similar documents to a given query vector.
     *
     * @param queryVector The vector to find similar documents for.
     * @param topK        The maximum number of similar documents to return.
     * @param filter      Optional metadata filter to apply to the search. Can be null or empty.
     *                    The exact structure of the filter map would depend on the underlying vector store's capabilities.
     * @return A CompletableFuture containing a list of {@link Document} objects representing the search results,
     * ordered by similarity (highest score first).
     */
    CompletableFuture<List<Document>> query(List<Double> queryVector,
                                            int topK,
                                            Map<String, Object> filter);

    /**
     * Deletes documents from the vector store by their IDs.
     * (Optional for initial implementations, can be a no-op).
     *
     * @param ids A list of unique identifiers for the documents to delete.
     * @return A CompletableFuture that completes when the delete operation is finished.
     */
    CompletableFuture<Void> delete(List<String> ids);

    /**
     * Clears all documents from the vector store.
     *
     * @return A CompletableFuture that completes when the clear operation is finished.
     */
    CompletableFuture<Void> clearAll();
}
