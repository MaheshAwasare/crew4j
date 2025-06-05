package com.javaagentai.aiagents.memory;

import java.util.List;
import java.util.Map;

public interface Memory {
    /**
     * Adds a key-value pair to the memory.
     * @param key The key to store the data under.
     * @param value The value to store.
     */
    void add(String key, Object value);

    /**
     * Adds multiple key-value pairs to the memory.
     * @param data A map containing key-value pairs to store.
     */
    void add(Map<String, Object> data);

    /**
     * Retrieves an object from memory by its key.
     * @param key The key of the data to retrieve.
     * @return The object associated with the key, or null if not found.
     */
    Object get(String key);

    /**
     * Retrieves all items from memory.
     * @return A list of all values stored in memory.
     *         Consider returning List<Map.Entry<String, Object>> or a custom MemoryEntry class
     *         if key-value pairs are needed. For now, List<Object> for simplicity.
     */
    List<Object> getAll(); // Or List<Map.Entry<String, Object>>

    /**
     * Searches memory for items relevant to a query.
     * @param query The search query.
     * @param topK The maximum number of relevant items to return.
     * @return A list of relevant values.
     */
    List<Object> search(String query, int topK); // Or List<Map.Entry<String, Object>>

    /**
     * Clears all items from memory.
     */
    void clear();
}
