package com.javaagentai.aiagents.services.embedding;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

/**
 * Mock implementation of EmbeddingClient for testing and development.
 * Generates simple, deterministic, or random vectors based on input text.
 */
public class MockEmbeddingClient implements EmbeddingClient {

    private final int dimension;
    private final Random random = new Random(42); // Seeded for deterministic random numbers if needed

    public MockEmbeddingClient() {
        this(4); // Default dimension
    }

    public MockEmbeddingClient(int dimension) {
        if (dimension <= 0) {
            throw new IllegalArgumentException("Dimension must be positive.");
        }
        this.dimension = dimension;
        System.out.println("MockEmbeddingClient initialized with dimension: " + dimension + ". This is a mock implementation and does not generate meaningful embeddings.");
    }

    @Override
    public CompletableFuture<List<Double>> embed(String text) {
        if (text == null) {
            return CompletableFuture.completedFuture(generateRandomVector());
        }
        // Simple deterministic embedding based on text properties
        List<Double> vector = new ArrayList<>(dimension);

        // Example: Use text length and hashcode for first two dimensions
        vector.add((double) text.length());
        vector.add((double) (text.hashCode() % 1000)); // Modulo to keep it somewhat bounded

        // Use character values for subsequent dimensions, or random if text is too short
        for (int i = 2; i < dimension; i++) {
            if (text.length() > (i - 2)) {
                vector.add((double) text.charAt(i - 2));
            } else {
                // Fallback to a value derived from length or a random value if text is too short
                vector.add(random.nextDouble() * 100); // Scaled random number
            }
        }
        
        // Ensure vector is of the correct dimension, padding with 0.0 or truncating if necessary
        // This loop handles cases where initial logic produces fewer than `dimension` elements
        while (vector.size() < dimension) {
            vector.add(random.nextDouble() * 50); // Pad with more random values
        }
        // Truncate if initial logic produced more (shouldn't happen with current logic but good for safety)
        if (vector.size() > dimension) {
            vector = vector.subList(0, dimension);
        }

        // System.out.println("MockEmbeddingClient: Generated mock vector for text: \"" + text.substring(0, Math.min(text.length(), 20)) + "...\"");
        return CompletableFuture.completedFuture(vector);
    }

    private List<Double> generateRandomVector() {
        return DoubleStream.generate(() -> random.nextDouble() * 100)
                           .limit(dimension)
                           .boxed()
                           .collect(Collectors.toList());
    }

    @Override
    public CompletableFuture<List<List<Double>>> embed(List<String> texts) {
        if (texts == null) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
        List<CompletableFuture<List<Double>>> futures = texts.stream()
                .map(this::embed) // Calls the single text embed method
                .collect(Collectors.toList());

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join) // .join() is safe after allOf
                        .collect(Collectors.toList()));
    }
}
