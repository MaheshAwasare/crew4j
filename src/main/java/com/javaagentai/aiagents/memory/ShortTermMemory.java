package com.javaagentai.aiagents.memory;

import java.util.*;
import java.util.stream.Collectors;

public class ShortTermMemory implements Memory {
    private final Map<String, Object> memoryStore;
    private final int capacity; // Optional: to limit memory size

    public ShortTermMemory() {
        this(Integer.MAX_VALUE); // Default to a very large capacity
    }

    public ShortTermMemory(int capacity) {
        this.capacity = capacity;
        // Use LinkedHashMap to maintain insertion order and allow for LRU eviction if capacity is exceeded
        this.memoryStore = new LinkedHashMap<String, Object>(capacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Object> eldest) {
                return size() > ShortTermMemory.this.capacity;
            }
        };
    }

    @Override
    public void add(String key, Object value) {
        if (key == null || value == null) {
            // Or throw IllegalArgumentException
            System.err.println("ShortTermMemory: Key or value cannot be null.");
            return;
        }
        synchronized (memoryStore) {
            memoryStore.put(key, value);
        }
    }

    @Override
    public void add(Map<String, Object> data) {
        if (data == null) return;
        synchronized (memoryStore) {
            data.forEach(this::add); // Use the single add method for consistent handling
        }
    }

    @Override
    public Object get(String key) {
        if (key == null) return null;
        synchronized (memoryStore) {
            return memoryStore.get(key);
        }
    }

    @Override
    public List<Object> getAll() {
        synchronized (memoryStore) {
            return new ArrayList<>(memoryStore.values());
        }
    }

    /**
     * Simple keyword search. Checks if the query string is contained in the key or
     * in the string representation of the value.
     *
     * @param query The search query.
     * @param topK  The maximum number of relevant items to return.
     * @return A list of relevant values.
     */
    @Override
    public List<Object> search(String query, int topK) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String lowerCaseQuery = query.toLowerCase();
        List<Object> results;
        synchronized (memoryStore) {
            results = memoryStore.entrySet().stream()
                    .filter(entry -> entry.getKey().toLowerCase().contains(lowerCaseQuery) ||
                                     String.valueOf(entry.getValue()).toLowerCase().contains(lowerCaseQuery))
                    .map(Map.Entry::getValue)
                    .collect(Collectors.toList());
        }
        // Reverse to get most recent first if LinkedHashMap maintains insertion order
        // Collections.reverse(results); // This might be counter-intuitive if not explicitly stated
        return results.stream().limit(topK).collect(Collectors.toList());
    }

    @Override
    public void clear() {
        synchronized (memoryStore) {
            memoryStore.clear();
        }
    }

    public int size() {
        synchronized (memoryStore) {
            return memoryStore.size();
        }
    }
}
