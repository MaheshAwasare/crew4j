package com.javaagentai.aiagents.core;

import com.javaagentai.aiagents.llm.LLMClient;
import com.javaagentai.aiagents.memory.Memory;
import com.javaagentai.aiagents.memory.ShortTermMemory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ConsensualProcessTest {

    static final String CONTRIBUTOR_A_NAME = "ContributorAgentA";
    static final String CONTRIBUTOR_B_NAME = "ContributorAgentB";
    static final String SYNTHESIZER_AGENT_NAME = "SynthesizerAgent";

    static final String PERSPECTIVE_A = "Perspective A: AI enhances creativity and automates mundane tasks.";
    static final String PERSPECTIVE_B = "Perspective B: AI could lead to job displacement and privacy concerns.";
    static final String INITIAL_TASK_DESC = "Analyze the dual impact of AI on society.";
    static final String SYNTHESIZED_ANSWER = "Synthesized Answer: AI enhances creativity and automates tasks, but also poses risks like job displacement and privacy concerns.";


    static class MockContributorLLMClient implements LLMClient {
        private final String agentName;
        private final String perspective;

        public MockContributorLLMClient(String agentName, String perspective) {
            this.agentName = agentName;
            this.perspective = perspective;
        }

        @Override
        public String complete(String prompt) {
            // Ensure the prompt is for the initial task.
            if (prompt.contains(INITIAL_TASK_DESC)) {
                return perspective;
            }
            return "Error: MockContributorLLMClient for " + agentName + " received unexpected prompt: " + prompt;
        }


        public String complete(String prompt, Map<String, Object> options) { return complete(prompt); }
        @Override
        public void close() {}
    }

    static class MockSynthesizerLLMClient implements LLMClient {
        @Override
        public String complete(String prompt) {
            // Check if this is the synthesis phase
            if (prompt.contains("Synthesize a final answer for the original task") && prompt.contains(INITIAL_TASK_DESC)) {
                // Simple check for presence of perspectives, a real LLM would parse and synthesize.
                // This mock will just confirm it got the right kind of prompt.
                boolean hasPerspectiveA = prompt.contains(PERSPECTIVE_A.substring(PERSPECTIVE_A.indexOf(":")+2)); // Check for content after "Perspective A: "
                boolean hasPerspectiveB = prompt.contains(PERSPECTIVE_B.substring(PERSPECTIVE_B.indexOf(":")+2)); // Check for content after "Perspective B: "

                if (hasPerspectiveA && hasPerspectiveB) {
                    return SYNTHESIZED_ANSWER;
                } else {
                    return "Error: Synthesizer did not receive all expected perspectives in prompt. A: " + hasPerspectiveA + ", B: " + hasPerspectiveB;
                }
            }
            return "Error: MockSynthesizerLLMClient received unexpected prompt: " + prompt;
        }

        public String complete(String prompt, Map<String, Object> options) { return complete(prompt); }
        @Override
        public void close() {}
    }

    private static BasicAgent contributorAgentA;
    private static BasicAgent contributorAgentB;
    private static BasicAgent synthesizerAgent;
    private static Memory memoryA, memoryB, memorySynth;

    @BeforeAll
    void setUpAll() {
        memoryA = new ShortTermMemory();
        memoryB = new ShortTermMemory();
        memorySynth = new ShortTermMemory();




        contributorAgentA = BasicAgent.builder()
                .name(CONTRIBUTOR_A_NAME)
                .role("Perspective Provider A")
                .llmClient( new MockContributorLLMClient(CONTRIBUTOR_A_NAME, PERSPECTIVE_A))
                .memory(memoryA)
                .tools(Collections.emptyList()).build();


        contributorAgentB = BasicAgent.builder()
                .name(CONTRIBUTOR_B_NAME)
                .role("Perspective Provider A")
                .llmClient( new MockContributorLLMClient(CONTRIBUTOR_B_NAME, PERSPECTIVE_B))
                .memory(memoryB)
                .tools(Collections.emptyList()).build();

        synthesizerAgent = BasicAgent.builder()
                .name(SYNTHESIZER_AGENT_NAME)
                .role("Insight Synthesizer")
                .llmClient(new MockSynthesizerLLMClient())
                .memory(memorySynth)
                .tools(Collections.emptyList()).build();

    }

    @AfterAll
    void tearDownAll() {
        if (contributorAgentA != null) contributorAgentA.shutdown();
        if (contributorAgentB != null) contributorAgentB.shutdown();
        if (synthesizerAgent != null) synthesizerAgent.shutdown();
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void testConsensualExecutionFlow() throws InterruptedException, ExecutionException, TimeoutException {
        AgentContext context = new AgentContext();
        memoryA.clear(); memoryB.clear(); memorySynth.clear();

        CompletableFuture<TaskResult> callbackFuture = new CompletableFuture<>();

        Task initialTask = Task.builder()
                .description(INITIAL_TASK_DESC)
                .input( Map.of("context", "Modern society"))
                .requiresHumanInput(false)
                .build();

        initialTask.setCallback(result -> {
            context.log("CONSENSUAL_TEST_CALLBACK: Task " + initialTask.getId() + " " + result.status() +
                        (result.error() != null ? " Error: " + result.error() : " Output: " + result.output()));
            callbackFuture.complete(result);
        });

        List<Agent> agents = List.of(contributorAgentA, contributorAgentB, synthesizerAgent); // Synthesizer last

        ConsensualProcess consensualProcess = new ConsensualProcess();
        CompletableFuture<String> finalResultFuture = consensualProcess.execute(initialTask, agents, context);

        // Assertions
        String finalResult = finalResultFuture.get(10, TimeUnit.SECONDS);
        assertEquals(SYNTHESIZED_ANSWER, finalResult, "Final result from process should match expected synthesized output.");

        // The status of the initialTask object itself might be COMPLETED (by one of the parallel executions or the synthesizer)
        // or its callback might be what matters more. The ConsensualProcess passes the initialTask's callback to the synthesis task.
        assertEquals(TaskStatus.PENDING, initialTask.getStatus(), "Initial task status should be COMPLETED by the synthesizer's final action.");

        TaskResult callbackResult = callbackFuture.get(5, TimeUnit.SECONDS);
        assertNotNull(callbackResult, "Callback should have been triggered.");
        assertEquals(TaskStatus.COMPLETED, callbackResult.status(), "Callback result status should be COMPLETED.");
        assertEquals(SYNTHESIZED_ANSWER, callbackResult.output(), "Callback output should match the final synthesized answer.");

        // Verify logs
        List<String> logMessages = context.getLogHistory().stream().map(AgentContext.LogEntry::message).collect(Collectors.toList());
        assertTrue(logMessages.stream().anyMatch(msg -> msg.contains("CONSENSUAL_PROCESS: Agent " + CONTRIBUTOR_A_NAME + " starting parallel execution")), "Contributor A start log missing.");
        assertTrue(logMessages.stream().anyMatch(msg -> msg.contains("CONSENSUAL_PROCESS: Agent " + CONTRIBUTOR_B_NAME + " starting parallel execution")), "Contributor B start log missing.");
        assertTrue(logMessages.stream().anyMatch(msg -> msg.contains("CONSENSUAL_PROCESS: Agent " + CONTRIBUTOR_A_NAME + " completed task " + initialTask.getId() + ". Output: " + PERSPECTIVE_A)), "Contributor A completion log missing.");
        assertTrue(logMessages.stream().anyMatch(msg -> msg.contains("CONSENSUAL_PROCESS: Agent " + CONTRIBUTOR_B_NAME + " completed task " + initialTask.getId() + ". Output: " + PERSPECTIVE_B)), "Contributor B completion log missing.");
        assertTrue(logMessages.stream().anyMatch(msg -> msg.contains("CONSENSUAL_PROCESS: Asking synthesizer agent " + SYNTHESIZER_AGENT_NAME + " to synthesize final answer")), "Synthesizer start log missing.");

        // Verify memories
        // BasicAgent stores "task_summary:" + task.getId() + ":" + task.getDescription()
        // For contributors, the task ID in memory will be that of the agentSpecificTask created in ConsensualProcess.
        // We can't easily get those IDs here. So we'll check if their perspectives are stored against *any* task ID for that description.
        
        boolean memoryAPopulated = memoryA.getAll().stream()
            .anyMatch(val -> val instanceof String && ((String)val).equals(PERSPECTIVE_A));
        assertTrue(memoryAPopulated, "Contributor A memory should contain its perspective.");

        boolean memoryBPopulated = memoryB.getAll().stream()
            .anyMatch(val -> val instanceof String && ((String)val).equals(PERSPECTIVE_B));
        assertTrue(memoryBPopulated, "Contributor B memory should contain its perspective.");

        // Synthesizer agent memory should contain the final synthesized answer.
        // The task description for synthesis is complex, so we check if *any* stored summary matches.
        boolean memorySynthPopulated = memorySynth.getAll().stream()
            .anyMatch(val -> val instanceof String && ((String)val).equals(SYNTHESIZED_ANSWER));
        assertTrue(memorySynthPopulated, "Synthesizer memory should contain the final synthesized answer.");
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testConsensualWithOneAgent() throws InterruptedException, ExecutionException, TimeoutException {
        AgentContext context = new AgentContext();
        memoryA.clear(); // Using Contributor A's memory

        CompletableFuture<TaskResult> callbackFuture = new CompletableFuture<>();

        Task initialTask = Task.builder()
                .description(INITIAL_TASK_DESC)
                .input( Map.of("context", "Single agent scenario"))
                .expectedOutput("Perspective A expected.")
                .requiresHumanInput(false)
                .build();

        initialTask.setCallback(result -> {
            context.log("SINGLE_AGENT_CONSENSUAL_TEST_CALLBACK: Task " + initialTask.getId() + " " + result.status() + " Output: " + result.output());
            callbackFuture.complete(result);
        });


        List<Agent> singleAgentList = List.of(contributorAgentA);
        ConsensualProcess consensualProcess = new ConsensualProcess();
        CompletableFuture<String> finalResultFuture = consensualProcess.execute(initialTask, singleAgentList, context);

        String finalResult = finalResultFuture.get(3, TimeUnit.SECONDS);
        assertEquals(PERSPECTIVE_A, finalResult, "Result should be Contributor A's direct perspective.");
        
        assertEquals(TaskStatus.COMPLETED, initialTask.getStatus(), "Task status should be COMPLETED.");
        
        TaskResult callbackResult = callbackFuture.get(1, TimeUnit.SECONDS);
        assertNotNull(callbackResult, "Callback should have triggered for single agent.");
        assertEquals(TaskStatus.COMPLETED, callbackResult.status(), "Callback status should be COMPLETED for single agent.");
        assertEquals(PERSPECTIVE_A, callbackResult.output(), "Callback output should match Contributor A's perspective.");

        // Memory check
        boolean memoryAPopulated = memoryA.getAll().stream()
            .anyMatch(val -> val instanceof String && ((String)val).equals(PERSPECTIVE_A));
        assertTrue(memoryAPopulated, "Contributor A memory should contain its perspective in single agent scenario.");
    }
}
