package com.javaagentai.aiagents.core;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

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

                        // Prepare the next task. The output of the current task becomes the description of the next.
                        // The last agent's output is the final result, so no new task is created after the last agent.
                        // This check needs to be smarter if we are inside a loop of agents.
                        // For now, we assume this loop is the main sequence.
                        // The creation of the next task should only happen if this isn't the last agent.
                        // This logic will be handled by returning the output string and the loop outside.
                        return new Task(output, Map.copyOf(context.getSharedMemory()), currentTask.getExpectedOutput(), null, TaskStatus.PENDING, currentTask.getCallback());
                    })
                    .exceptionally(ex -> {
                        context.log("SEQUENTIAL_PROCESS_ASYNC: Agent " + agent.getName() + " failed task: " + currentTask.getDescription() + ". Error: " + ex.getMessage());
                        // currentTask.setStatus(TaskStatus.FAILED); // Set by agent.performTask
                        // The callback is now handled within agent.performTask's exceptional path
                        // if (currentTask.getCallback() != null) {
                        //    currentTask.getCallback().accept(new TaskResult(TaskStatus.FAILED, null, ex.getMessage()));
                        // }
                        // Propagate a null task or a special marker to skip subsequent agents.
                        // Or rethrow to stop the chain: throw new RuntimeException(ex);
                        return null; // Returning null to indicate failure to the next step in chain
                    });
            });
        }

        // After the loop, the taskChain holds a CompletableFuture for the last created Task object.
        // We need its description (which is the output of the last agent that successfully ran).
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
