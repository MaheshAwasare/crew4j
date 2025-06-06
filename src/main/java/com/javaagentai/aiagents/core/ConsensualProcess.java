package com.javaagentai.aiagents.core;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class ConsensualProcess implements Process {

    @Override
    public CompletableFuture<String> execute(Task initialTask, List<Agent> agents, AgentContext context) {
        context.log("CONSENSUAL_PROCESS: Starting execution for task: " + initialTask.getDescription() + " (ID: " + initialTask.getId() + ")");

        if (agents == null || agents.isEmpty()) {
            context.log("CONSENSUAL_PROCESS: No agents available. Failing task " + initialTask.getId());
            initialTask.setStatus(TaskStatus.FAILED);
            if (initialTask.getCallback() != null) {
                initialTask.getCallback().accept(new TaskResult(TaskStatus.FAILED, null, "No agents available for consensual process."));
            }
            return CompletableFuture.completedFuture("Error: No agents available.");
        }

        if (agents.size() == 1) {
            Agent singleAgent = agents.get(0);
            context.log("CONSENSUAL_PROCESS: Only one agent (" + singleAgent.getName() + ") available. Delegating task " + initialTask.getId() + " directly.");
            // Task status and callback are handled within performTask
            return singleAgent.performTask(initialTask, context);
        }

        // All agents (including the eventual synthesizer) perform the initial task in parallel.
        List<CompletableFuture<Map.Entry<String, String>>> individualExecutionFutures = new ArrayList<>();

        for (Agent agent : agents) {
            // Create a new task instance for each agent to prevent shared state issues,
            // especially around status and callbacks if the initialTask object is reused directly.
            // The description, input, and expected output are from the original task.
            // No specific callback for these individual runs; the overall process handles the final callback.
            Task agentSpecificTask = Task.builder()
                    .description(initialTask.getDescription())
                    .input(initialTask.getInput())
                    .expectedOutput(initialTask.getExpectedOutput())
                    .assignedAgent(agent)
                    .status(TaskStatus.PENDING)
                    .requiresHumanInput(initialTask.isRequiresHumanInput())
                    .build();

            // If HITL is required, and human input is already present on initialTask, pass it.
            if (initialTask.getHumanInput() != null) {
                agentSpecificTask.setHumanInput(initialTask.getHumanInput());
            }


            context.log("CONSENSUAL_PROCESS: Agent " + agent.getName() + " starting parallel execution for task " + initialTask.getId());
            CompletableFuture<String> agentOutputFuture = agent.performTask(agentSpecificTask, context);
            
            individualExecutionFutures.add(
                agentOutputFuture.handle((output, ex) -> {
                    if (ex != null) {
                        context.log("CONSENSUAL_PROCESS: Agent " + agent.getName() + " failed for task " + initialTask.getId() + ". Error: " + ex.getMessage());
                        return new AbstractMap.SimpleEntry<>(agent.getName(), "Error: " + ex.getMessage());
                    }
                    context.log("CONSENSUAL_PROCESS: Agent " + agent.getName() + " completed task " + initialTask.getId() + ". Output: " + output);
                    return new AbstractMap.SimpleEntry<>(agent.getName(), output);
                })
            );
        }

        CompletableFuture<Void> allOfFutures = CompletableFuture.allOf(
            individualExecutionFutures.toArray(new CompletableFuture[0])
        );

        return allOfFutures.thenComposeAsync(v -> {
            List<Map.Entry<String, String>> individualResults = individualExecutionFutures.stream()
                .map(CompletableFuture::join) // .join() is safe here because allOfFutures ensures completion
                .collect(Collectors.toList());

            context.log("CONSENSUAL_PROCESS: All agents completed parallel execution for task " + initialTask.getId());

            // Synthesis Step
            Agent synthesizerAgent = agents.get(agents.size() - 1); // Last agent is the synthesizer
            StringBuilder synthesisPromptDetails = new StringBuilder("Synthesize a final answer for the original task based on the following perspectives:\n");
            for (Map.Entry<String, String> entry : individualResults) {
                synthesisPromptDetails.append("\n- Agent ").append(entry.getKey()).append(" said: '").append(entry.getValue()).append("'");
            }

            String synthesisTaskDescription = String.format(
                "Original Task: %s\n%s",
                initialTask.getDescription(),
                synthesisPromptDetails.toString()
            );

                    Task synthesisTask = Task.builder()
                            .description(synthesisTaskDescription)
                            .input(initialTask.getInput())
                            .expectedOutput(initialTask.getExpectedOutput())
                            .assignedAgent(synthesizerAgent)
                            .status(TaskStatus.PENDING)
                            .callback(initialTask.getCallback())
                            .requiresHumanInput(false)
                            .build();

                    context.log("CONSENSUAL_PROCESS: Asking synthesizer agent " + synthesizerAgent.getName() + " to synthesize final answer for task " + initialTask.getId());
            return synthesizerAgent.performTask(synthesisTask, context);

        }, agents.get(0).getMemory() != null ? ((BasicAgent)agents.get(0)).llmExecutor : Runnable::run) // Use an executor from an agent if possible, or a default one
        .exceptionally(ex -> {
            context.log("CONSENSUAL_PROCESS: An error occurred during the consensual process for task " + initialTask.getId() + ". Error: " + ex.getMessage());
            initialTask.setStatus(TaskStatus.FAILED);
            if (initialTask.getCallback() != null) {
                initialTask.getCallback().accept(new TaskResult(TaskStatus.FAILED, null, "Consensual process failed: " + ex.getMessage()));
            }
            return "Error: Consensual process failed. " + ex.getMessage();
        });
    }
}
