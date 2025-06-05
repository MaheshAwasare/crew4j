package com.javaagentai.aiagents.services.embedding;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for embedding clients that convert text into numerical vector representations.
 */
public interface EmbeddingClient {

    /**
     * Creates an embedding for a single piece of text.
     *
     * @param text The text to embed.
     * @return A CompletableFuture containing a list of doubles representing the embedding.
     */
    CompletableFuture<List<Double>> embed(String text);

    /**
     * Creates embeddings for a list of texts.
     *
     * @param texts The list of texts to embed.
     * @return A CompletableFuture containing a list of lists of doubles, where each inner list
     *         is an embedding for the corresponding text in the input list.
     */
    CompletableFuture<List<List<Double>>> embed(List<String> texts);
}
