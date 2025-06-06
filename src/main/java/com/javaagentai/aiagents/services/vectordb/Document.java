package com.javaagentai.aiagents.services.vectordb;

import java.util.Map;
import java.util.Objects;

/**
 * Represents a document stored in or retrieved from a vector store.
 *
 * @param id          A unique identifier for the document.
 * @param score       The relevance score of the document, typically populated when retrieved from a search.
 *                    May be 0.0 or a default value if not applicable (e.g., when creating a document for upsert).
 * @param metadata    A map of key-value pairs providing additional information about the document.
 * @param textContent The original text content of the document.
 */
public record Document(
        String id,
        double score,
        Map<String, Object> metadata,
        String textContent
) {
    /**
     * Compact constructor for validation.
     */
    public Document {
        Objects.requireNonNull(id, "Document ID cannot be null.");
        // score can be any double value.
        Objects.requireNonNull(metadata, "Metadata map cannot be null, use Collections.emptyMap() if no metadata.");
        // textContent can be null if not applicable, though generally expected.
    }

    /**
     * Convenience constructor for creating a document primarily for storage (score might not be relevant yet).
     *
     * @param id          The unique identifier for the document.
     * @param metadata    A map of key-value pairs.
     * @param textContent The original text content.
     */
    public Document(String id, Map<String, Object> metadata, String textContent) {
        this(id, 0.0, metadata, textContent); // Default score to 0.0
    }
}
