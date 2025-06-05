package com.javaagentai.aiagents.core;

public record TaskResult(TaskStatus status, String output, String error) {
    // Constructor for success cases (no error)
    public TaskResult(TaskStatus status, String output) {
        this(status, output, null);
    }
}
