package com.javaagentai.aiagents.services.embedding;

import com.javaagentai.aiagents.config.Config; // Import Config

public class EmbeddingClientFactory {

    /**
     * Creates an EmbeddingClient instance based on the specified client type.
     * Configuration details (e.g., dimension for MockEmbeddingClient) are fetched from Config.java.
     *
     * @param clientType The type of the embedding client (e.g., "MockEmbeddingClient").
     * @return An instance of EmbeddingClient.
     * @throws IllegalArgumentException if the client type is unsupported or required configuration is missing.
     */
    public static EmbeddingClient createClient(String clientType) {
        if (clientType == null || clientType.trim().isEmpty()) {
            throw new IllegalArgumentException("Embedding client type cannot be null or empty.");
        }

        switch (clientType.toLowerCase()) {
            case "mockembeddingclient":
                // Use a general mock dimension first, then try specific if available.
                // This logic could be more sophisticated based on context (e.g., which LTM is being configured).
                // For now, we'll use the general mock dimension.
                int dimension = Config.getMockEmbeddingDimension(); 
                System.out.println("EmbeddingClientFactory: Creating MockEmbeddingClient with dimension: " + dimension);
                return new MockEmbeddingClient(dimension);

            // case "openaiembeddingclient":
            //     String apiKey = Config.getLlmApiKey("OpenAI"); // Assuming same API key for embeddings
            //     String model = Config.getString("embedding.openai.model", "text-embedding-ada-002"); // Example
            //     if (apiKey == null || apiKey.trim().isEmpty()) {
            //         throw new IllegalArgumentException("OpenAI API key is missing for EmbeddingClient.");
            //     }
            //     System.out.println("EmbeddingClientFactory: Would create OpenAIEmbeddingClient with model: " + model);
            //     // return new OpenAIEmbeddingClient(apiKey, model); // When implemented
            //     throw new UnsupportedOperationException("OpenAIEmbeddingClient not yet implemented.");
            
            // Add other client types here as they are implemented
            // case "vertexaiembeddingclient":
            //    // ...
            //    throw new UnsupportedOperationException("VertexAIEmbeddingClient not yet implemented.");

            default:
                throw new IllegalArgumentException("Unsupported EmbeddingClient type: " + clientType);
        }
    }

    /**
     * Creates an EmbeddingClient for the client type specified for FileBasedLTM in Config.java.
     * @return An instance of EmbeddingClient.
     */
    public static EmbeddingClient createFileBasedLtmEmbeddingClient() {
        String clientType = Config.getFileBasedLtmEmbeddingClientType();
        if ("MockEmbeddingClient".equalsIgnoreCase(clientType)) {
             int dimension = Config.getFileBasedLtmEmbeddingMockDimension();
             System.out.println("EmbeddingClientFactory: Creating MockEmbeddingClient for FileBasedLTM with dimension: " + dimension);
             return new MockEmbeddingClient(dimension);
        }
        // Add other types if FileBasedLTM can use them
        return createClient(clientType); // Fallback to general creation
    }

    /**
     * Creates an EmbeddingClient for the client type specified for ChromaDBLTM in Config.java.
     * @return An instance of EmbeddingClient.
     */
    public static EmbeddingClient createChromaDbLtmEmbeddingClient() {
        String clientType = Config.getChromaDbLtmEmbeddingClientType();
         if ("MockEmbeddingClient".equalsIgnoreCase(clientType)) {
             int dimension = Config.getChromaDbLtmEmbeddingMockDimension();
             System.out.println("EmbeddingClientFactory: Creating MockEmbeddingClient for ChromaDBLTM with dimension: " + dimension);
             return new MockEmbeddingClient(dimension);
        }
        // Add other types if ChromaDB can use them
        return createClient(clientType); // Fallback to general creation
    }
}
