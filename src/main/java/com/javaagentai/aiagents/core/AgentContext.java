package com.javaagentai.aiagents.core;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent context that holds context information for Agent during execution
 */
public class AgentContext {

    // Inner record for structured log entries
    public record LogEntry(LocalDateTime timestamp, String message) {}

    private final Map<String, Object> sharedMemory = new ConcurrentHashMap<>();
    private final List<LogEntry> logHistory = new ArrayList<>();
    private final Map<String, Map<String, Object>> taskScopedMemory = new ConcurrentHashMap<>();

    /**
     * Stores a key-value pair in the general shared memory.
     * This memory is accessible across different tasks and agents.
     *
     * @param key   the key with which the specified value is to be associated
     * @param value the value to be associated with the specified key
     */
    public void storeSharedData(String key, Object value) {
        sharedMemory.put(key, value);
    }

    /**
     * Retrieves the value to which the specified key is mapped from the general shared memory,
     * or {@code null} if this map contains no mapping for the key.
     *
     * @param key the key whose associated value is to be returned
     * @return the value to which the specified key is mapped, or
     *         {@code null} if this map contains no mapping for the key
     */
    public Object retrieveSharedData(String key) {
        return sharedMemory.get(key);
    }

    /**
     * Stores a key-value pair scoped to a specific task.
     *
     * @param taskId The identifier of the task.
     * @param key    The key for the data.
     * @param value  The data to store.
     */
    public void storeTaskData(String taskId, String key, Object value) {
        taskScopedMemory.computeIfAbsent(taskId, k -> new ConcurrentHashMap<>()).put(key, value);
    }

    /**
     * Retrieves data for a specific key within a task's scope.
     *
     * @param taskId The identifier of the task.
     * @param key    The key of the data to retrieve.
     * @return The data associated with the key for the task, or null if not found.
     */
    public Object retrieveTaskData(String taskId, String key) {
        Map<String, Object> taskData = taskScopedMemory.get(taskId);
        return (taskData != null) ? taskData.get(key) : null;
    }

    /**
     * Retrieves all data associated with a specific task.
     *
     * @param taskId The identifier of the task.
     * @return A map of all data for the task. Returns an empty map if no data exists for the task.
     */
    public Map<String, Object> getTaskScopedData(String taskId) {
        return taskScopedMemory.getOrDefault(taskId, Collections.emptyMap());
    }

    /**
     * Logs a message with the current timestamp.
     * The log entry is added to the history.
     *
     * @param message The message to log.
     */
    public void log(String message) {
        synchronized (logHistory) { // Synchronize access if multiple threads might log
            logHistory.add(new LogEntry(LocalDateTime.now(), message));
        }
    }

    /**
     * Retrieves the history of log entries.
     * Each entry includes a timestamp and the logged message.
     *
     * @return An unmodifiable list of log entries.
     */
    public List<LogEntry> getLogHistory() {
        synchronized (logHistory) {
            return Collections.unmodifiableList(new ArrayList<>(logHistory));
        }
    }

    /**
     * Returns a view of the shared memory map.
     *
     * @return the shared memory map
     */
    public Map<String, Object> getSharedMemory() {
        return Collections.unmodifiableMap(sharedMemory);
    }

    /**
     * Clears all data from task-scoped memory for a specific task.
     * @param taskId The identifier of the task whose data is to be cleared.
     */
    public void clearTaskData(String taskId) {
        taskScopedMemory.remove(taskId);
    }

    /**
     * Clears all shared data.
     */
    public void clearSharedData() {
        sharedMemory.clear();
    }

    /**
     * Clears all log history.
     */
    public void clearLogHistory() {
        synchronized (logHistory) {
            logHistory.clear();
        }
    }
}