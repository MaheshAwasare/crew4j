package com.javaagentai.aiagents.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.javaagentai.aiagents.llm.LLMClient;
import com.javaagentai.aiagents.memory.Memory;
import com.javaagentai.aiagents.tools.Tool;
import lombok.Builder;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
@Builder
public class BasicAgent implements Agent {

    private final String name;
    private final String role;
    private final List<Tool> tools;
    private final LLMClient llmClient;
    private final Memory memory;
    public final ExecutorService llmExecutor;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final int MAX_ITERATIONS = 5;

    public record LLMToolCall(String tool_name, Map<String, Object> tool_parameters) {}

    public BasicAgent(String name, String role, List<Tool> tools, LLMClient llmClient, Memory memory) {
        this.name = name;
        this.role = role;
        this.tools = tools;
        this.llmClient = llmClient;
        this.memory = memory;
        this.llmExecutor = Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors()));
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getRole() {
        return role;
    }

    @Override
    public List<Tool> getTools() {
        return tools;
    }

    @Override
    public Memory getMemory() {
        return memory;
    }

    @Override
    public CompletableFuture<String> performTask(Task task, AgentContext context) {
        context.log(name + " received task: " + task.getDescription() + " (ID: " + task.getId() + ")");
        task.setAssignedAgent(this); // Assign agent first

        // HITL Check
        if (task.isRequiresHumanInput() && task.getHumanInput() == null) {
            task.setStatus(TaskStatus.AWAITING_HUMAN_INPUT);
            context.log(name + " is AWAITING HUMAN INPUT for task: " + task.getDescription() + " (ID: " + task.getId() + ")");
            
            CompletableFuture<String> humanInputCompletionFuture = new CompletableFuture<>();
            task.setExternalCompletionHandle(humanInputCompletionFuture); // Store the handle in the task

            // Return a future that is chained to humanInputCompletionFuture
            // When humanInputCompletionFuture is completed (by task.setHumanInput), this chain will proceed.
            return humanInputCompletionFuture.thenApplyAsync(humanProvidedInput -> {
                context.log(name + " received human input for task " + task.getId() + ": " + humanProvidedInput);
                // Task status should have been set to IN_PROGRESS by setHumanInput
                // Treat human input as the direct result of this agent's step for this task.
                // Store this human input in memory as if it were a final answer for this step.
                this.memory.add("human_input_received:" + task.getId() + ":" + task.getDescription(), humanProvidedInput);
                
                // Complete the task with the human input as the result.
                // The callback of the original task object will be triggered.
                // Note: If further processing of human_input by LLM was needed, this logic would be different.
                // For this iteration, human_input is the final output for this agent's step.
                task.setStatus(TaskStatus.COMPLETED); // Mark as completed since human input is the answer for this step
                if (task.getCallback() != null) {
                    task.getCallback().accept(new TaskResult(TaskStatus.COMPLETED, humanProvidedInput));
                }
                context.storeTaskData(task.getId(), name + "_human_input_result", humanProvidedInput);
                return humanProvidedInput;
            }, llmExecutor).exceptionally(ex -> {
                context.log(name + " failed while processing human input for task " + task.getId() + ". Error: " + ex.getMessage());
                task.setStatus(TaskStatus.FAILED);
                this.memory.add("human_input_failure:" + task.getId(), ex.getMessage());
                if (task.getCallback() != null) {
                    task.getCallback().accept(new TaskResult(TaskStatus.FAILED, null, ex.getMessage()));
                }
                return "Error processing human input: " + ex.getMessage();
            });
        }

        // If not requiring human input, or if human input is already provided, proceed with normal flow.
        task.setStatus(TaskStatus.IN_PROGRESS); // Set to IN_PROGRESS if not awaiting human input
        StringBuilder conversationHistory = new StringBuilder();
        if (task.getHumanInput() != null) {
            // If human input was provided and we are past the HITL check, it means it was set before performTask was called,
            // or this task doesn't require HITL. If it was set for a task that *did* require HITL, the above block would have handled it.
            // For now, we can log it or add to conversation history if needed.
            context.log(name + " proceeding with task " + task.getId() + ", human input was previously provided: " + task.getHumanInput());
            // Optionally add to conversation history if LLM should be aware of it:
            // conversationHistory.append("\nHuman provided input: ").append(task.getHumanInput());
            // However, the current HITL logic assumes human input *is* the answer for the step.
        }
        
        AtomicInteger iterationCount = new AtomicInteger(0);
        return CompletableFuture.supplyAsync(() -> buildInitialPrompt(task, context, conversationHistory.toString()), llmExecutor)
                .thenComposeAsync(prompt -> processLlmInteraction(prompt, task, context, conversationHistory, iterationCount), llmExecutor);
    }

    private CompletableFuture<String> processLlmInteraction(String currentPrompt, Task task, AgentContext context, StringBuilder conversationHistory, AtomicInteger iterationCount) {
        if (iterationCount.incrementAndGet() > MAX_ITERATIONS) {
            context.log(name + " reached max iterations for task: " + task.getDescription() + " (ID: " + task.getId() + ")");
            task.setStatus(TaskStatus.FAILED);
            this.memory.add("task_failure_max_iterations:" + task.getId() + ":" + task.getDescription(), "Agent reached maximum iterations.");
            if (task.getCallback() != null) {
                task.getCallback().accept(new TaskResult(TaskStatus.FAILED, null, "Agent reached maximum iterations."));
            }
            return CompletableFuture.completedFuture("Error: Agent reached maximum iterations.");
        }

        context.log(name + " sending prompt to LLM (iteration " + iterationCount.get() + ") for task " + task.getId() + ":\n" + currentPrompt);
        String llmResponse = llmClient.complete(currentPrompt);
        context.log(name + " received LLM response for task " + task.getId() + ": " + llmResponse);

        Optional<LLMToolCall> toolCallOpt = parseToolCall(llmResponse, context);

        if (toolCallOpt.isPresent()) {
            LLMToolCall toolCall = toolCallOpt.get();
            Optional<Tool> selectedToolOpt = tools.stream().filter(t -> t.getName().equals(toolCall.tool_name())).findFirst();

            if (selectedToolOpt.isPresent()) {
                Tool selectedTool = selectedToolOpt.get();
                context.log(name + " attempting to use tool: " + selectedTool.getName() + " with params: " + toolCall.tool_parameters() + " for task " + task.getId());

                return selectedTool.use(toolCall.tool_parameters())
                    .handleAsync((toolResult, toolError) -> {
                        if (toolError != null) {
                            String errorMsg = toolError.getMessage();
                            context.log(name + " tool execution failed for task " + task.getId() + ": " + errorMsg);
                            conversationHistory.append("\nTool ").append(selectedTool.getName()).append(" execution failed: ").append(errorMsg);
                            this.memory.add("tool_error:" + selectedTool.getName() + ":" + task.getId(), errorMsg);
                        } else {
                            context.log(name + " tool " + selectedTool.getName() + " executed for task " + task.getId() + ". Result: " + toolResult);
                            conversationHistory.append("\nTool ").append(selectedTool.getName()).append(" output: ").append(toolResult);
                            this.memory.add("tool_interaction:" + selectedTool.getName() + ":" + task.getId(), toolResult);
                        }
                        String nextPrompt = buildFollowUpPrompt(task, context, conversationHistory.toString());
                        return nextPrompt;
                    }, llmExecutor)
                    .thenComposeAsync(nextPrompt -> processLlmInteraction(nextPrompt, task, context, conversationHistory, iterationCount), llmExecutor);
            } else {
                context.log(name + " LLM tried to use unknown tool: " + toolCall.tool_name() + " for task " + task.getId());
                conversationHistory.append("\nAttempted to use unknown tool: ").append(toolCall.tool_name());
                this.memory.add("unknown_tool_attempt:" + toolCall.tool_name() + ":" + task.getId(), llmResponse);
                String nextPrompt = buildFollowUpPrompt(task, context, conversationHistory.toString());
                return processLlmInteraction(nextPrompt, task, context, conversationHistory, iterationCount);
            }
        } else {
            context.log(name + " received final answer from LLM for task " + task.getId() + ": " + llmResponse);
            task.setStatus(TaskStatus.COMPLETED);
            this.memory.add("task_summary:" + task.getId() + ":" + task.getDescription(), llmResponse);
            if (task.getCallback() != null) {
                task.getCallback().accept(new TaskResult(TaskStatus.COMPLETED, llmResponse));
            }
            context.storeTaskData(task.getId(), name + "_final_output", llmResponse);
            return CompletableFuture.completedFuture(llmResponse);
        }
    }

    private Optional<LLMToolCall> parseToolCall(String llmResponse, AgentContext context) {
        System.out.println("LLM RESPONSE: " + llmResponse);
        String trimmedResponse = llmResponse.trim();

        // Normalize markdown fences if any
        if (trimmedResponse.startsWith("```json")) {
            trimmedResponse = trimmedResponse.substring(7).trim();
        }
        if (trimmedResponse.endsWith("```")) {
            trimmedResponse = trimmedResponse.substring(0, trimmedResponse.length() - 3).trim();
        }

        // Look for the block that starts with our known tool schema
        int schemaStart = trimmedResponse.indexOf("{");
        while (schemaStart != -1) {
            int schemaEnd = trimmedResponse.indexOf("}", schemaStart);
            if (schemaEnd == -1) break;

            // Try to find full JSON object from schemaStart
            String possibleJson = extractFullJsonBlock(trimmedResponse.substring(schemaStart));
            if (possibleJson != null && possibleJson.contains("\"tool_name\"") && possibleJson.contains("\"tool_parameters\"")) {
                try {
                    LLMToolCall toolCall = objectMapper.readValue(possibleJson, LLMToolCall.class);
                    if (toolCall.tool_name() != null && !toolCall.tool_name().isEmpty()) {
                        context.log(name + " parsed tool call: " + toolCall.tool_name());
                        return Optional.of(toolCall);
                    }
                } catch (JsonProcessingException e) {
                    context.log(name + " failed to parse tool JSON: " + e.getMessage());
                    context.log("Offending block: " + possibleJson);
                }
            }
            // Try next '{'
            schemaStart = trimmedResponse.indexOf("{", schemaStart + 1);
        }

        context.log(name + " could not detect tool invocation in LLM response.");
        return Optional.empty();
    }
    private String extractFullJsonBlock(String text) {
        int openBraces = 0;
        int endIndex = -1;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') openBraces++;
            else if (c == '}') openBraces--;

            if (openBraces == 0) {
                endIndex = i;
                break;
            }
        }
        return (endIndex != -1) ? text.substring(0, endIndex + 1) : null;
    }
    private String buildToolDescriptions(List<Tool> tools) {
        StringBuilder sb = new StringBuilder();
        for (Tool tool : tools) {
            sb.append("Tool: ").append(tool.getName()).append("\n");
            sb.append("Description: ").append(tool.getDescription()).append("\n");
            sb.append("Expected Parameters:\n");
            for (Map.Entry<String, String> entry : tool.getParameterSchema().entrySet()) {
                sb.append("  - ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    private String buildInitialPrompt(Task task, AgentContext context, String history) {
        String toolDescriptions = buildToolDescriptions(tools);

        List<Object> memoryResults = this.memory.search(task.getDescription(), 3);
        String memoryContext = "No relevant information found in memory.";
        if (memoryResults != null && !memoryResults.isEmpty()) {
            memoryContext = memoryResults.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining("\n- ", "Previously recorded information that might be relevant:\n- ", ""));
        }

        String prompt = String.format(
                "You are an AI agent with the name '%s' and role '%s'.\n" +
                        "Your current task is: %s (Task ID: %s)\n" + // Added Task ID to prompt
                        "Input Data for the task: %s\n\n" +
                        "Relevant Information from Memory:\n%s\n\n" +
                        "You have the following tools available:\n%s\n\n" +
                        "To use a tool, respond *only* with a JSON object in the format:\n" +
                        "{\n" +
                        "  \"tool_name\": \"tool_name_here\",\n" +
                        "  \"tool_parameters\": { \"param1_name\": \"param1_value\", ... }\n" +
                        "}\n\n" +
                        "If you do not need to use a tool and have the final answer for the task '%s', provide your answer directly as a string.\n\n" +
                        "Conversation History (including previous tool outputs or errors):\n%s\n\n" +
                        "What is your next step or final answer?",
                name, role, task.getDescription(), task.getId(), task.getInput().toString(),
                memoryContext,
                toolDescriptions.isEmpty() ? "No tools available." : toolDescriptions,
                task.getDescription(),
                history.isEmpty() ? "No history yet." : history
        );
        System.out.println("PROMPT GENERATED");
        return prompt;

    }

    private String buildFollowUpPrompt(Task task, AgentContext context, String history) {
        return buildInitialPrompt(task, context, history);
    }

    public void shutdown() {
        System.out.println(name + " shutting down executor.");
        llmExecutor.shutdown();
        try {
            if (!llmExecutor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                llmExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            llmExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
