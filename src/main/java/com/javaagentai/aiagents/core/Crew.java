package com.javaagentai.aiagents.core;

import lombok.Builder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

@Builder
public class Crew {
    @Builder.Default
    private  List<Agent> agents = new ArrayList<>();
    private  ProcessStrategy processStrategy;
    private  Process process;
    // No global context here; it's per execution.

    public Crew(List<Agent> agents, ProcessStrategy strategy) {
        Objects.requireNonNull(agents, "Agents list cannot be null.");
        Objects.requireNonNull(strategy, "Process strategy cannot be null.");
        // It's fine if agents list is empty, Process should handle it.

        this.agents = agents;
        this.processStrategy = strategy;

        switch (strategy) {
            case SEQUENTIAL:
                this.process = new SequentialProcess(); // This now returns CompletableFuture
                break;
            case HIERARCHICAL:
                this.process = new HierarchicalProcess(); // Placeholder
                break;
            // case PARALLEL: // Placeholder
            //     throw new UnsupportedOperationException("Parallel process strategy not yet implemented.");
            default:
                throw new IllegalArgumentException("Unsupported process strategy: " + strategy);
        }
    }

    public CompletableFuture<String> execute(Task initialTask) {
        Objects.requireNonNull(initialTask, "Initial task cannot be null.");
        AgentContext context = new AgentContext(); // Fresh context for each execution
        context.log("CREW_ASYNC: Starting execution with strategy: " + this.processStrategy + " for task: " + initialTask.getDescription());

        // The process.execute method now returns a CompletableFuture
        return this.process.execute(initialTask, this.agents, context)
            .thenApply(finalResult -> {
                context.log("CREW_ASYNC: Execution finished. Final result: " + finalResult);
                // Example of accessing logs, could be useful for debugging or post-processing
                // context.getLogHistory().forEach(logEntry -> System.out.println(logEntry.timestamp() + " [CREW_ASYNC_LOG]: " + logEntry.message()));
                return finalResult;
            })
            .exceptionally(ex -> {
                context.log("CREW_ASYNC: Execution failed for task: " + initialTask.getDescription() + ". Error: " + ex.getMessage());
                // Depending on requirements, might rethrow or return an error marker string
                return "Error during crew execution: " + ex.getMessage();
            });
    }

    public List<Agent> getAgents() {
        return agents;
    }

    public ProcessStrategy getProcessStrategy() {
        return processStrategy;
    }

    // Optional: Shutdown agents if they have resources (like BasicAgent's ExecutorService)
    public void shutdown(AgentContext context) {
        context.log("CREW_ASYNC: Initiating shutdown of agents.");
        for (Agent agent : agents) {
            if (agent instanceof BasicAgent) { // Check if agent is BasicAgent and has shutdown
                ((BasicAgent) agent).shutdown();
            }
            // Add other agent type shutdowns if necessary
        }
        context.log("CREW_ASYNC: Agents shutdown process completed.");
    }
}
