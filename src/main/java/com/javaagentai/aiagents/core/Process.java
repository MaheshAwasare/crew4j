package com.javaagentai.aiagents.core;

import java.util.List;
import java.util.concurrent.CompletableFuture;


/**
 * Author: Mahesh Awasare
 */
public interface Process {
    /**
     * Executes the orchestration logic for tasks among agents asynchronously.
     *
     * @param initialTask The initial task to be processed.
     * @param agents      The list of agents available in the crew.
     * @param context     The shared context for agents.
     * @return A CompletableFuture representing the final result or summary of the process.
     */
    CompletableFuture<String> execute(Task initialTask, List<Agent> agents, AgentContext context);
}
