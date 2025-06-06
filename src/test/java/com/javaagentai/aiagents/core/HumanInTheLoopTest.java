package com.javaagentai.aiagents.core;

import com.javaagentai.aiagents.llm.LLMClient;
import com.javaagentai.aiagents.memory.Memory;
import com.javaagentai.aiagents.memory.ShortTermMemory;
import com.javaagentai.aiagents.tools.Tool; // Though not used directly, BasicAgent needs it
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout; // For test-level timeout

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException; // For specific future.get timeout
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class HumanInTheLoopTest {

    static final String MOCK_LLM_RESPONSE = "This is a mock LLM response.";
    static final String HUMAN_INPUT_TEXT = "This is the human's direct answer.";

    static class MockHitlLLMClient implements LLMClient {
        @Override
        public String complete(String prompt) {
            // This should ideally not be called if HITL is active and input is missing.
            // For the non-HITL test, it will be called.
            return MOCK_LLM_RESPONSE;
        }


        public String complete(String prompt, Map<String, Object> options) {
            return complete(prompt);
        }

        @Override
        public void close() {
            // No-op
        }
    }

    private static BasicAgent hitlAgent;
    private static Memory agentMemory;

    @BeforeAll
    void setUpAll() {
        agentMemory = new ShortTermMemory(10);
        LLMClient mockLLMClient = new MockHitlLLMClient();
        // BasicAgent constructor: String name, String role, List<Tool> tools, LLMClient llmClient, Memory memory
        hitlAgent = BasicAgent.builder()
                .name("HitlTestAgent")
                .role("Human Interaction Specialist")
                .tools(Collections.emptyList())
                .llmClient(mockLLMClient)
                .memory(agentMemory)
                .build();
    }

    @AfterAll
    void tearDownAll() {
        if (hitlAgent != null) {
            hitlAgent.shutdown();
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS) // Overall test timeout
    void testHumanInputRequiredAndProvided() throws InterruptedException, ExecutionException, TimeoutException {
        AgentContext context = new AgentContext();
        agentMemory.clear(); // Ensure memory is clean for this test

        String taskDescription = "A task that needs human review before completion.";
        CompletableFuture<TaskResult> callbackFuture = new CompletableFuture<>();
        Task task = Task.builder()
                .description(taskDescription)
                .input(Map.of("data", "initial data for task"))
                .expectedOutput("Expected human-verified output.")
                .requiresHumanInput(true)
                .build();

        task.setCallback(result -> {
            context.log("HITL_TEST_CALLBACK: Task " + task.getId() + " " + result.status() +
                        (result.error() != null ? " Error: " + result.error() : " Output: " + result.output()));
            callbackFuture.complete(result);
        });

        SequentialProcess process = new SequentialProcess();
        CompletableFuture<String> finalResultFuture = process.execute(task, List.of(hitlAgent), context);

        // Verify Paused State
        // Give a very short time for the process to set the AWAITING_HUMAN_INPUT state
        Thread.sleep(200); // Allow async operations to reach pause point

        assertEquals(TaskStatus.AWAITING_HUMAN_INPUT, task.getStatus(), "Task status should be AWAITING_HUMAN_INPUT.");
        assertFalse(finalResultFuture.isDone(), "Process future should not be completed while awaiting human input.");

        // Provide Human Input
        task.setHumanInput(HUMAN_INPUT_TEXT);

        // Verify Resumption and Completion
        String finalResult = finalResultFuture.get(5, TimeUnit.SECONDS); // Wait for completion after human input
        assertEquals(HUMAN_INPUT_TEXT, finalResult, "The final result should be the human input.");

        assertEquals(TaskStatus.COMPLETED, task.getStatus(), "Task status should be COMPLETED after human input.");

        TaskResult callbackResult = callbackFuture.get(1, TimeUnit.SECONDS); // Callback should also complete
        assertNotNull(callbackResult, "Callback should have been triggered.");
        assertEquals(TaskStatus.COMPLETED, callbackResult.status(), "Callback result status should be COMPLETED.");
        assertEquals(HUMAN_INPUT_TEXT, callbackResult.output(), "Callback output should match the human input.");

        // Verify agent memory
        // BasicAgent stores "human_input_received:" + task.getId() + ":" + task.getDescription()
        assertNotNull(agentMemory.get("human_input_received:" + task.getId() + ":" + task.getDescription()),
                "Agent memory should contain an entry for the human input received.");
        assertEquals(HUMAN_INPUT_TEXT, agentMemory.get("human_input_received:" + task.getId() + ":" + task.getDescription()));

        // Verify logs
        List<String> logMessages = context.getLogHistory().stream().map(AgentContext.LogEntry::message).collect(Collectors.toList());
        assertTrue(logMessages.stream().anyMatch(msg -> msg.contains("AWAITING HUMAN INPUT for task: " + taskDescription)), "Log should show agent awaiting input.");
        assertTrue(logMessages.stream().anyMatch(msg -> msg.contains("received human input for task " + task.getId())), "Log should show agent received human input.");
        assertTrue(logMessages.stream().anyMatch(msg -> msg.contains("HITL_TEST_CALLBACK: Task " + task.getId() + " COMPLETED Output: " + HUMAN_INPUT_TEXT)), "Callback log for completion missing or incorrect.");
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS) // Overall test timeout
    void testHumanInputNotRequired() throws InterruptedException, ExecutionException, TimeoutException {
        AgentContext context = new AgentContext();
        agentMemory.clear();

        String taskDescription = "A standard task that completes via LLM.";
        CompletableFuture<TaskResult> callbackFuture = new CompletableFuture<>();


        Task task = Task.builder()
                .description(taskDescription)
                .input(Map.of("data", "initial data for task"))
                .expectedOutput("Expected LLM output.")
                .requiresHumanInput(true)
                .build();

        task.setCallback(result -> {
            context.log("NON_HITL_TEST_CALLBACK: Task " + task.getId() + " " + result.status() +
                        (result.error() != null ? " Error: " + result.error() : " Output: " + result.output()));
            callbackFuture.complete(result);
        });


        SequentialProcess process = new SequentialProcess();
        CompletableFuture<String> finalResultFuture = process.execute(task, List.of(hitlAgent), context);

        // Verify Completion without pause
        String finalResult = finalResultFuture.get(3, TimeUnit.SECONDS);
        assertEquals(MOCK_LLM_RESPONSE, finalResult, "The final result should be the mock LLM response.");

        assertNotEquals(TaskStatus.AWAITING_HUMAN_INPUT, task.getStatus(), "Task status should not have been AWAITING_HUMAN_INPUT.");
        assertEquals(TaskStatus.COMPLETED, task.getStatus(), "Task status should be COMPLETED.");

        TaskResult callbackResult = callbackFuture.get(1, TimeUnit.SECONDS);
        assertNotNull(callbackResult, "Callback should have been triggered for non-HITL task.");
        assertEquals(TaskStatus.COMPLETED, callbackResult.status(), "Callback result status should be COMPLETED for non-HITL task.");
        assertEquals(MOCK_LLM_RESPONSE, callbackResult.output(), "Callback output should match mock LLM response for non-HITL task.");

        // Verify agent memory - BasicAgent stores "task_summary:"
        assertNotNull(agentMemory.get("task_summary:" + task.getId() + ":" + task.getDescription()),
                "Agent memory should contain task summary for non-HITL task.");
        assertEquals(MOCK_LLM_RESPONSE, agentMemory.get("task_summary:" + task.getId() + ":" + task.getDescription()));
        
        // Verify logs
        List<String> logMessages = context.getLogHistory().stream().map(AgentContext.LogEntry::message).collect(Collectors.toList());
        assertFalse(logMessages.stream().anyMatch(msg -> msg.contains("AWAITING HUMAN INPUT")), "Log should NOT show agent awaiting input for non-HITL task.");
        assertTrue(logMessages.stream().anyMatch(msg -> msg.contains("received final answer from LLM for task " + task.getId())), "Log should show agent received final answer from LLM.");
    }
}
