package com.javaagentai.aiagents.core;


/**
 * Author - Mahesh Awasare
 * Represents the result of a task execution.
 * @param status
 * @param output
 * @param error
 */
public record TaskResult(TaskStatus status, String output, String error) {
    // Constructor for success cases (no error)
    public TaskResult(TaskStatus status, String output) {
        this(status, output, null);
    }
}
