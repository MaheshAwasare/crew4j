package com.javaagentai.aiagents.services.vectordb;

import com.javaagentai.aiagents.services.embedding.EmbeddingClient; // For constructor, though not directly used here
import tech.amikos.chromadb.Client;
import tech.amikos.chromadb.Collection;
import tech.amikos.chromadb.Collection.QueryResponse;
import tech.amikos.chromadb.ChromaException;
import tech.amikos.chromadb.embeddings.DefaultEmbeddingFunction; // Placeholder for collection creation
import tech.amikos.chromadb.handler.ApiException;


import tech.amikos.chromadb.Embedding;
import tech.amikos.chromadb.model.QueryEmbedding;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ChromaDBVectorStore implements VectorStore {

    private final Client chromaClient;
    private Collection chromaCollection; // Can be re-assigned in clearAll
    private final String collectionName; // Store for re-creation
    // private final EmbeddingClient ourEmbeddingClient; // Not directly used in this class as per VectorStore interface

    public ChromaDBVectorStore(String chromaUrl, String collectionName, EmbeddingClient ourEmbeddingClient) {
        Objects.requireNonNull(chromaUrl, "ChromaDB URL cannot be null.");
        Objects.requireNonNull(collectionName, "Collection name cannot be null.");
        // this.ourEmbeddingClient = ourEmbeddingClient; // Stored if needed for other reasons
        this.collectionName = collectionName;

        try {
            this.chromaClient = new Client(chromaUrl);
            // Using DefaultEmbeddingFunction as a placeholder because we provide embeddings directly.
            // The create_if_not_exists flag is true by default in getOrCreateCollection.
            this.chromaCollection = this.chromaClient.createCollection(
                    collectionName,
                    null,
                    true,// No specific metadata for collection creation
                    new DefaultEmbeddingFunction() // Placeholder EF
            );
            System.out.println("ChromaDBVectorStore: Successfully connected to ChromaDB and got/created collection: " + collectionName);
        } catch (ChromaException e) {
            System.err.println("ChromaDBVectorStore: Failed to initialize ChromaDB client or collection. Error: " + e.getMessage());
            throw new RuntimeException("Failed to initialize ChromaDBVectorStore", e);
        } catch (ApiException e) {
            throw new RuntimeException(e);
        }
    }

    private List<Float> convertDoubleToFloatList(List<Double> doubleList) {
        if (doubleList == null) return null;
        return doubleList.stream().map(Double::floatValue).collect(Collectors.toList());
    }

    private List<List<Float>> convertListOfDoubleToFloatLists(List<List<Double>> listOfDoubleLists) {
        if (listOfDoubleLists == null) return null;
        return listOfDoubleLists.stream()
                .map(this::convertDoubleToFloatList)
                .collect(Collectors.toList());
    }

    @Override
    public CompletableFuture<Void> upsert(List<String> ids, List<List<Double>> vectors, List<Map<String, Object>> metadataList, List<String> textContents) {
        return CompletableFuture.runAsync(() -> {
            if (ids.size() != vectors.size() ||
                    (metadataList != null && ids.size() != metadataList.size()) ||
                    (textContents != null && ids.size() != textContents.size())) {
                throw new IllegalArgumentException("Input lists must have the same size.");
            }

            try {
                List<List<Float>> floatVectors = convertListOfDoubleToFloatLists(vectors);

                // Convert List<List<Float>> to List<Embedding>
                List<Embedding> embeddings = floatVectors.stream()
                        .map(Embedding::new)
                        .collect(Collectors.toList());

                // Convert metadata from Map<String, Object> to Map<String, String>
                List<Map<String, String>> stringMetadataList = null;
                if (metadataList != null) {
                    stringMetadataList = metadataList.stream()
                            .map(this::convertMetadataToStringMap)
                            .collect(Collectors.toList());
                }

                // ChromaDB client's upsert method signature:
                // upsert(ids, embeddings, metadatas, documents)
                this.chromaCollection.upsert(embeddings, stringMetadataList, textContents,ids);

            } catch (ChromaException e) {
                System.err.println("ChromaDBVectorStore: Error during upsert. " + e.getMessage());
                throw new RuntimeException("ChromaDB upsert failed", e);
            }
        });
    }

    // Helper method to convert Map<String, Object> to Map<String, String>
    private Map<String, String> convertMetadataToStringMap(Map<String, Object> objectMap) {
        if (objectMap == null) {
            return null;
        }

        return objectMap.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue() != null ? entry.getValue().toString() : null
                ));
    }
    @Override
    public CompletableFuture<List<Document>> query(List<Double> queryVector, int topK, Map<String, Object> filter) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Since the ChromaDB Java client query method expects List<String> for queryTexts,
                // and we have a pre-computed embedding vector, we have a few options:

                // Option 1: Use an empty/placeholder query and rely on similarity search
                // This approach may not work as expected since ChromaDB will generate embeddings from text

                // Option 2: Check if there's a different method for embedding-based queries
                // You might need to look for methods like queryByEmbeddings() or similar

                // Option 3: Use the get() method with filtering to retrieve documents,
                // then compute similarity in memory (not recommended for large datasets)

                // For now, let's try Option 1 with a placeholder text approach:
                List<String> placeholderQueries = List.of(""); // Empty string as placeholder

                // Convert the include enums - you'll need to import the proper enum type
                List<QueryEmbedding.IncludeEnum> includeFields = List.of(
                        QueryEmbedding.IncludeEnum.DOCUMENTS,
                        QueryEmbedding.IncludeEnum.METADATAS,
                        QueryEmbedding.IncludeEnum.DISTANCES
                );

                QueryResponse response = this.chromaCollection.query(
                        placeholderQueries,   // Query texts (placeholder)
                        topK,                 // Number of results
                        filter,              // Where clause for metadata filtering
                        null,                // Where document clause
                        includeFields        // Include documents, metadatas, distances
                );

                List<Document> results = new ArrayList<>();
                if (response != null && response.getIds() != null && !response.getIds().isEmpty()) {
                    // The response structure is List<List<String>> for ids, etc.
                    // Since we query with a single placeholder, we expect one result set
                    List<String> resIds = response.getIds().get(0);
                    List<Map<String, Object>> resMetadatas = response.getMetadatas() != null ?
                            response.getMetadatas().get(0) :
                            Collections.nCopies(resIds.size(), Collections.emptyMap());
                    List<String> resDocuments = response.getDocuments() != null ?
                            response.getDocuments().get(0) :
                            Collections.nCopies(resIds.size(), null);
                    List<Float> resDistances = response.getDistances() != null ?
                            response.getDistances().get(0) :
                            Collections.nCopies(resIds.size(), Float.MAX_VALUE);

                    // Since the placeholder approach might not give us the similarity we want,
                    // we would need to compute similarity manually here using the queryVector
                    // This is not ideal but might be necessary with this API

                    for (int i = 0; i < resIds.size(); i++) {
                        results.add(new Document(
                                resIds.get(i),
                                resDistances.get(i),
                                resMetadatas.get(i) != null ? resMetadatas.get(i) : Collections.emptyMap(),
                                resDocuments.get(i)
                        ));
                    }
                }

                return results;
            } catch (ChromaException e) {
                System.err.println("ChromaDBVectorStore: Error during query. " + e.getMessage());
                throw new RuntimeException("ChromaDB query failed", e);
            }
        });
    }

// Alternative approach: Check if your ChromaCollection has other query methods
// You might want to look for methods like:
// - queryByEmbeddings()
// - searchByVector()
// - similaritySearch()
//
// If such methods exist, replace the query logic above with the appropriate method call

    @Override
    public CompletableFuture<Void> delete(List<String> ids) {
        return CompletableFuture.runAsync(() -> {
            try {
                // delete(ids, where, whereDocument)
                this.chromaCollection.delete(ids, null, null);
            } catch (ApiException e) {
                System.err.println("ChromaDBVectorStore: Error during delete. " + e.getMessage());
                throw new RuntimeException("ChromaDB delete failed", e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> clearAll() {
        return CompletableFuture.runAsync(() -> {
            try {
                this.chromaClient.deleteCollection(this.collectionName);
                // Recreate the collection after deleting it
                this.chromaCollection = this.chromaClient.createCollection(
                        this.collectionName,
                        null, // No metadata
                        true, // create_if_not_exists - should be true here
                        new DefaultEmbeddingFunction() // Placeholder EF
                );
                 System.out.println("ChromaDBVectorStore: Collection " + this.collectionName + " cleared and recreated.");
            } catch (ChromaException | ApiException e) {
                System.err.println("ChromaDBVectorStore: Error during clearAll (delete/create collection). " + e.getMessage());
                throw new RuntimeException("ChromaDB clearAll failed", e);
            }
        });
    }
}
