package com.javaagentai.aiagents.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;


/**
 * This enum represents the different strategies for processing the input data.
 * ProcessStrategy
 */
public class HierarchicalProcess implements Process {

    // Helper Records for parsing manager's plan
    // Ensure these are public or accessible if used outside, or keep them private if only internal
    record SubTaskDetail(String task_description, String assigned_agent_name, String expected_output) {
    }

    record ManagerPlan(List<SubTaskDetail> sub_tasks, String manager_notes) {
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public CompletableFuture<String> execute(Task initialTask, List<Agent> agents, AgentContext context) {
        context.log("HIERARCHICAL_PROCESS: Starting execution for task: " + initialTask.getDescription());

        if (agents == null || agents.isEmpty()) {
            context.log("HIERARCHICAL_PROCESS: No agents available. Failing task.");
            initialTask.setStatus(TaskStatus.FAILED);
            if (initialTask.getCallback() != null) {
                initialTask.getCallback().accept(new TaskResult(TaskStatus.FAILED, null, "No agents available for hierarchical process."));
            }
            return CompletableFuture.completedFuture("Error: No agents available.");
        }

        Agent managerAgent = agents.get(0);
        List<Agent> workerAgents = agents.stream().skip(1).collect(Collectors.toList());

        if (workerAgents.isEmpty() && agents.size() == 1) {
            context.log("HIERARCHICAL_PROCESS: Only one agent (manager) available. Delegating task directly.");
            // Manager processes the task directly without delegation.
            // The BasicAgent's prompt should ideally detect it has no workers and just solve the task.
            return managerAgent.performTask(initialTask, context);
        }

        if (workerAgents.isEmpty() && agents.size() > 1) {
            context.log("HIERARCHICAL_PROCESS: Manager agent identified, but no worker agents available. Manager will attempt to solve directly.");
            // This case implies the setup is for hierarchical but workers are missing.
            // Manager might still be prompted to break down, but won't be able to delegate.
            // For simplicity, let manager solve it.
            return managerAgent.performTask(initialTask, context);
        }


        // Step 1: Manager Agent's First Pass (Planning)
        context.log("HIERARCHICAL_PROCESS: Asking manager " + managerAgent.getName() + " to plan sub-tasks.");
        // The initialTask for the manager should guide it to break down the task.
        // Its prompt (handled by BasicAgent) needs to be tailored for this.
        CompletableFuture<String> managerPlanJsonFuture = managerAgent.performTask(initialTask, context);

        return managerPlanJsonFuture.thenComposeAsync(planJson -> {
            context.log("HIERARCHICAL_PROCESS: Manager " + managerAgent.getName() + " produced plan: " + planJson);
            ManagerPlan managerPlan;
            try {
                managerPlan = objectMapper.readValue(planJson, ManagerPlan.class);
                if (managerPlan.sub_tasks() == null || managerPlan.sub_tasks().isEmpty()) {
                    context.log("HIERARCHICAL_PROCESS: Manager " + managerAgent.getName() + " did not define any sub-tasks. Attempting to get final answer from manager directly based on its output.");
                    // This could be treated as the manager deciding to solve it directly after analysis.
                    // The 'planJson' might actually be the final answer in this case.
                    return CompletableFuture.completedFuture(planJson);
                }
            } catch (JsonProcessingException e) {
                context.log("HIERARCHICAL_PROCESS: Failed to parse manager's plan. Error: " + e.getMessage() + ". Plan JSON: " + planJson);
                initialTask.setStatus(TaskStatus.FAILED);
                if (initialTask.getCallback() != null) {
                    initialTask.getCallback().accept(new TaskResult(TaskStatus.FAILED, null, "Failed to parse manager's plan."));
                }
                // Return the raw output from manager if parsing fails, or a specific error.
                return CompletableFuture.completedFuture("Error: Failed to parse manager's plan. Manager's output: " + planJson);
            }

            // Step 2: Sub-Task Execution (Sequential for now)
            Map<String, String> subTaskResults = new HashMap<>();
            CompletableFuture<Void> allSubTasksFuture = CompletableFuture.completedFuture(null);

            for (SubTaskDetail subTaskDetail : managerPlan.sub_tasks()) {
                allSubTasksFuture = allSubTasksFuture.thenComposeAsync(v -> {
                    Optional<Agent> assignedAgentOpt = workerAgents.stream()
                            .filter(agent -> agent.getName().equals(subTaskDetail.assigned_agent_name()))
                            .findFirst();

                    if (assignedAgentOpt.isEmpty()) {
                        context.log("HIERARCHICAL_PROCESS: Could not find assigned agent: " + subTaskDetail.assigned_agent_name() + " for sub-task: " + subTaskDetail.task_description());
                        subTaskResults.put(subTaskDetail.task_description(), "Error: Agent not found - " + subTaskDetail.assigned_agent_name());
                        return CompletableFuture.completedFuture(null); // Continue to next sub-task
                    }

                    Agent workerAgent = assignedAgentOpt.get();


                    Task subTask = Task.builder()
                            .description(subTaskDetail.task_description())
                            .input(initialTask.getInput())
                            .expectedOutput(subTaskDetail.expected_output())
                            .assignedAgent(workerAgent)
                            .status(TaskStatus.PENDING).build();
                    context.log("HIERARCHICAL_PROCESS: Assigning sub-task '" + subTask.getDescription() + "' to agent " + workerAgent.getName());
                    return workerAgent.performTask(subTask, context)
                            .thenAccept(result -> {
                                context.log("HIERARCHICAL_PROCESS: Sub-task '" + subTask.getDescription() + "' completed by " + workerAgent.getName() + ". Output: " + result);
                                subTaskResults.put(subTask.getDescription(), result);
                            }).exceptionally(ex -> {
                                context.log("HIERARCHICAL_PROCESS: Sub-task '" + subTask.getDescription() + "' failed for agent " + workerAgent.getName() + ". Error: " + ex.getMessage());
                                subTaskResults.put(subTask.getDescription(), "Error: " + ex.getMessage());
                                return null; // Continue to next sub-task even if one fails
                            });
                });
            }

            // Step 3: Final Result Aggregation (Manager's Second Pass)
            return allSubTasksFuture.thenComposeAsync(v -> {
                context.log("HIERARCHICAL_PROCESS: All sub-tasks processed. Preparing for manager synthesis.");
                StringBuilder synthesisPromptDetails = new StringBuilder("Synthesize the final answer for the original task based on the following sub-task results:\n");
                for (Map.Entry<String, String> entry : subTaskResults.entrySet()) {
                    synthesisPromptDetails.append("\n- Sub-task: ").append(entry.getKey())
                            .append("\n  Result: ").append(entry.getValue());
                }
                if (managerPlan.manager_notes() != null && !managerPlan.manager_notes().isEmpty()) {
                    synthesisPromptDetails.append("\n\nManager's initial notes for synthesis: ").append(managerPlan.manager_notes());
                }

                String synthesisTaskDescription = String.format(
                        "Original Task: %s\n%s",
                        initialTask.getDescription(),
                        synthesisPromptDetails.toString()
                );


                Task synthesisTask = Task.builder()
                        .description(synthesisTaskDescription)
                        .input(new HashMap<>())
                        .expectedOutput(initialTask.getExpectedOutput())
                        .assignedAgent(managerAgent)
                        .status(TaskStatus.PENDING).build();

                context.log("HIERARCHICAL_PROCESS: Asking manager " + managerAgent.getName() + " to synthesize final answer.");
                return managerAgent.performTask(synthesisTask, context);
            });

        }).exceptionally(ex -> {
            context.log("HIERARCHICAL_PROCESS: An error occurred in the process. Error: " + ex.getMessage());
            initialTask.setStatus(TaskStatus.FAILED);
            if (initialTask.getCallback() != null) {
                initialTask.getCallback().accept(new TaskResult(TaskStatus.FAILED, null, "Hierarchical process failed: " + ex.getMessage()));
            }
            return "Error: Hierarchical process failed. " + ex.getMessage();
        });
    }
}
