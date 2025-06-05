package com.javaagentai.aiagents.core;

import java.util.Map;
import java.util.UUID; // Added for ID
import java.util.concurrent.CompletableFuture; // Added for HITL
import java.util.function.Consumer;

public class Task {
    private final String id = UUID.randomUUID().toString(); // New field for unique ID
    private final String description;
    private final Map<String, Object> input;
    private final String expectedOutput;
    private Agent assignedAgent;
    private TaskStatus status;
    private Consumer<TaskResult> callback;

    // HITL specific fields
    private String humanInput = null; // New field
    private boolean requiresHumanInput = false; // New field
    private transient CompletableFuture<String> externalCompletionHandle; // New field for HITL

    // Constructor updated for requiresHumanInput
    public Task(String description, Map<String, Object> input, String expectedOutput, boolean requiresHumanInput) {
        this(description, input, expectedOutput, null, TaskStatus.PENDING, null, requiresHumanInput);
    }
    
    // Overloaded constructor without requiresHumanInput (defaults to false)
    public Task(String description, Map<String, Object> input, String expectedOutput) {
        this(description, input, expectedOutput, null, TaskStatus.PENDING, null, false);
    }


    // Full constructor updated for requiresHumanInput
    public Task(String description, Map<String, Object> input, String expectedOutput, Agent assignedAgent, TaskStatus initialStatus, Consumer<TaskResult> callback, boolean requiresHumanInput) {
        this.description = description;
        this.input = input;
        this.expectedOutput = expectedOutput;
        this.assignedAgent = assignedAgent;
        this.status = initialStatus != null ? initialStatus : TaskStatus.PENDING;
        this.callback = callback;
        this.requiresHumanInput = requiresHumanInput; // Initialize new field
    }
    
    // Constructor used by SequentialProcess, updated for requiresHumanInput
     public Task(String description, Map<String, Object> input, String expectedOutput, Agent assignedAgent, TaskStatus initialStatus, Consumer<TaskResult> callback) {
        this(description, input, expectedOutput, assignedAgent, initialStatus, callback, false); // Default requiresHumanInput to false
    }


    public String getId() { // Getter for ID
        return id;
    }

    public String getDescription() {
        return description;
    }

    public Map<String, Object> getInput() {
        return input;
    }

    public String getExpectedOutput() {
        return expectedOutput;
    }

    public Agent getAssignedAgent() {
        return assignedAgent;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public Consumer<TaskResult> getCallback() {
        return callback;
    }

    public boolean isRequiresHumanInput() { // Getter for requiresHumanInput
        return requiresHumanInput;
    }

    public void setRequiresHumanInput(boolean requiresHumanInput) { // Setter for requiresHumanInput
        this.requiresHumanInput = requiresHumanInput;
    }

    public String getHumanInput() { // Getter for humanInput
        return humanInput;
    }

    /**
     * Sets the human input for the task and attempts to complete the external handle.
     * @param humanInput The input provided by a human.
     */
    public void setHumanInput(String humanInput) {
        this.humanInput = humanInput;
        if (this.status == TaskStatus.AWAITING_HUMAN_INPUT) { // Only proceed if it was actually waiting
            this.setStatus(TaskStatus.IN_PROGRESS); // Or PENDING if it needs to be re-queued by a process manager
            if (this.externalCompletionHandle != null && !this.externalCompletionHandle.isDone()) {
                this.externalCompletionHandle.complete(humanInput);
                this.externalCompletionHandle = null; // Consume the handle
            }
        } else {
            // Log or handle cases where input is set but task was not awaiting it.
            System.err.println("Task " + id + ": Human input set, but task was not in AWAITING_HUMAN_INPUT status. Current status: " + this.status);
        }
    }

    public void setExternalCompletionHandle(CompletableFuture<String> future) { // Setter for externalCompletionHandle
        this.externalCompletionHandle = future;
    }


    public void setAssignedAgent(Agent assignedAgent) {
        this.assignedAgent = assignedAgent;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public void setCallback(Consumer<TaskResult> callback) {
        this.callback = callback;
    }

    public void completeTask(TaskResult result) {
        this.status = result.status();
        if (this.callback != null) {
            this.callback.accept(result);
        }
    }
}
