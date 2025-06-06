package com.javaagentai.aiagents.core;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * SequentialProcess is a Process implementation that executes tasks sequentially using CompletableFuture.
 * Author: Mahesh Awasare
 */
public class SequentialProcess implements Process {

    @Override
    public CompletableFuture<String> execute(Task initialTask, List<Agent> agents, AgentContext context) {
        if (agents == null || agents.isEmpty()) {
            context.log("SEQUENTIAL_PROCESS_ASYNC: No agents available to process the task.");
            if (initialTask != null) {
                initialTask.setStatus(TaskStatus.FAILED);
                if (initialTask.getCallback() != null) {
                    initialTask.getCallback().accept(new TaskResult(TaskStatus.FAILED, null, "No agents available."));
                }
            }
            return CompletableFuture.completedFuture("Error: No agents available.");
        }

        String taskId = initialTask.getDescription() + "_" + UUID.randomUUID().toString(); // Unique ID for this execution flow
        context.log("SEQUENTIAL_PROCESS_ASYNC: Starting process for task: " + initialTask.getDescription() + " with TaskID: " + taskId);

        // Start with a completed future holding the initial task
        CompletableFuture<Task> taskChain = CompletableFuture.completedFuture(initialTask);

        for (Agent agent : agents) {
            taskChain = taskChain.thenComposeAsync(currentTask -> {
                // If currentTask is null (e.g. from a failed previous step that didn't propagate task), handle it.
                if (currentTask == null) {
                    context.log("SEQUENTIAL_PROCESS_ASYNC: Skipping agent " + agent.getName() + " due to null task from previous step.");
                    return CompletableFuture.completedFuture(null); // Or handle error appropriately
                }

                currentTask.setAssignedAgent(agent);
                // No, status is set to IN_PROGRESS by the agent's performTask now.
                // currentTask.setStatus(TaskStatus.IN_PROGRESS); // Set by agent.performTask

                context.log("SEQUENTIAL_PROCESS_ASYNC: Agent " + agent.getName() + " starting task: " + currentTask.getDescription());
                context.storeTaskData(taskId, agent.getName() + "_input", currentTask.getInput());

                return agent.performTask(currentTask, context)
                        .thenApply(output -> {
                            // currentTask.setStatus(TaskStatus.COMPLETED); // Set by agent.performTask
                            context.storeTaskData(taskId, agent.getName() + "_output", output);
                            context.log("SEQUENTIAL_PROCESS_ASYNC: Agent " + agent.getName() + " finished task. Output: " + output);

                            // The callback is now handled within agent.performTask's success path
                            if (currentTask.getCallback() != null) {
                                TaskResult result = new TaskResult(TaskStatus.COMPLETED, output);
                                currentTask.getCallback().accept(result);
                            }


                            return Task.builder()
                                    .description(output)
                                    .input(Map.copyOf(context.getSharedMemory()))
                                    .expectedOutput(currentTask.getExpectedOutput())
                                    .status(TaskStatus.PENDING)
                                    .callback(currentTask.getCallback())
                                    .build();

                        })
                        .exceptionally(ex -> {
                            context.log("SEQUENTIAL_PROCESS_ASYNC: Agent " + agent.getName() + " failed task: " + currentTask.getDescription() + ". Error: " + ex.getMessage());

                            return null; // Returning null to indicate failure to the next step in chain
                        });
            });
        }


        return taskChain.thenApply(lastTask -> {
            if (lastTask == null || lastTask.getStatus() == TaskStatus.FAILED) {
                context.log("SEQUENTIAL_PROCESS_ASYNC: Process finished with failure or no result from the last agent.");
                return "Error: Process failed or produced no result.";
            }
            // The 'description' of this 'lastTask' object is the output of the *actual* last agent.
            String finalOutput = lastTask.getDescription();
            context.log("SEQUENTIAL_PROCESS_ASYNC: Process finished. Final output: " + finalOutput);
            return finalOutput;
        }).exceptionally(ex -> {
            context.log("SEQUENTIAL_PROCESS_ASYNC: Process chain failed. Error: " + ex.getMessage());
            return "Error: Process chain failed.";
        });
    }
}
