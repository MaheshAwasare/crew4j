package com.javaagentai.aiagents.core;

import com.javaagentai.aiagents.llm.GroqClient;
import com.javaagentai.aiagents.llm.LLMClient;
import com.javaagentai.aiagents.memory.Memory; // Added import
import com.javaagentai.aiagents.memory.ShortTermMemory; // Added import
import com.javaagentai.aiagents.tools.ExampleEchoTool;
import com.javaagentai.aiagents.tools.PdfWriterTool;
import com.javaagentai.aiagents.tools.Tool;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
/** 
 * Author: Mahesh Awasare 
 */
public class SequentialProcessTest {

    static class MockLLMClient implements LLMClient {
        private final String agentName;
        private final String fixedResponsePrefix;

        public MockLLMClient(String agentName, String fixedResponsePrefix) {
            this.agentName = agentName;
            this.fixedResponsePrefix = fixedResponsePrefix;
        }

        @Override
        public String complete(String prompt) {
            String taskDescription = "";
            // Check for "Relevant Information from Memory:" section to ensure it's being added
            assertTrue(prompt.contains("Relevant Information from Memory:"), "Prompt should contain memory section for " + agentName);
            
            if (prompt.contains("Task Description:")) {
                taskDescription = prompt.substring(prompt.indexOf("Task Description:") + "Task Description:".length()).trim();
                if (taskDescription.contains("\nInput Data:")) {
                    taskDescription = taskDescription.substring(0, taskDescription.indexOf("\nInput Data:")).trim();
                }
            } else if (prompt.contains("Your current task is:")) { // Adjusted to match BasicAgent's prompt format
                 taskDescription = prompt.substring(prompt.indexOf("Your current task is:") + "Your current task is:".length()).trim();
                 if (taskDescription.contains("\nInput Data for the task:")) {
                    taskDescription = taskDescription.substring(0, taskDescription.indexOf("\nInput Data for the task:")).trim();
                }
            }
            else {
                taskDescription = "Unknown Task (could not parse from prompt)";
            }
            return fixedResponsePrefix + taskDescription;
        }


        public String complete(String prompt, Map<String, Object> options) {
            return complete(prompt);
        }

        @Override
        public void close() {
            // No-op
        }
    }

    private static BasicAgent researcherAgent;
    private static BasicAgent writerAgent;
    private static ExampleEchoTool echoTool;
    private static Memory researcherMemory;
    private static Memory writerMemory;


    @BeforeAll
    void setUpAll() {
        echoTool = new ExampleEchoTool();
        List<Tool> researcherTools = Collections.singletonList(echoTool);
        List<Tool> writerTools = List.of(new PdfWriterTool());
        Agent researchAgent = BasicAgent.builder()
                .role("Research Assistant")
                .build();

        researcherMemory = new ShortTermMemory(10);
        researcherMemory.add("previous_finding:AI in Healthcare", "AI can predict patient deterioration.");// Initialize memory
        writerMemory = new ShortTermMemory(10);     // Initialize memory
        writerMemory.add("previous_finding:AI in Healthcare", "Writing");
        GroqClient researcherLLM = new GroqClient("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", "meta-llama/llama-4-scout-17b-16e-instruct");

        researcherAgent = BasicAgent.builder()
                .name("Researcher")
                .role("Data Research Specialist")
                .tools(researcherTools)
                .llmClient(researcherLLM)
                .memory(researcherMemory)
                .build();

        LLMClient writerLLM = new MockLLMClient("WriterAgent", "Writer processed: ");


        writerAgent = BasicAgent.builder()
                .name("Writer")
                .role("Content Generation Specialist")
                .tools(writerTools)
                .llmClient(writerLLM)
                .memory(writerMemory)
                .build();


        // Pre-populate researcher's memory for one of the tests if needed or to see it in prompt
        researcherMemory.add("previous_finding:AI in Healthcare", "AI can predict patient deterioration.");
    }

    @AfterAll
    void tearDownAll() {
        if (researcherAgent != null) {
            researcherAgent.shutdown();
        }
        if (writerAgent != null) {
            writerAgent.shutdown();
        }
    }

    @Test
    void testSequentialExecutionFlow() throws InterruptedException, ExecutionException, TimeoutException {
        AgentContext context = new AgentContext();
        List<Agent> agents = List.of(researcherAgent, writerAgent);

        // Clear memories before test run for isolation
        researcherMemory.clear();
        writerMemory.clear();

        researcherMemory.add("initial_context_for_research:AI in Healthcare", "Focus on diagnostics and treatment planning.");

        CompletableFuture<TaskResult> callbackFuture = new CompletableFuture<>();
        String taskDesc = "Research AI impact on healthcare and write a summary.";


        Task initialTask = Task.builder()
                .description(taskDesc)
                .input(Map.of("topic", "AI in Healthcare"))
                .expectedOutput("A concise summary of AI's impact on healthcare.")
                .status(TaskStatus.PENDING)
                .callback(result -> {
                    context.log("CALLBACK TRIGGERED: Task " + result.status() +
                            (result.error() != null ? " Error: " + result.error() : " Output: " + result.output()));
                    callbackFuture.complete(result);
                })
                .build();


        SequentialProcess sequentialProcess = new SequentialProcess();
        CompletableFuture<String> finalResultFuture = sequentialProcess.execute(initialTask, agents, context);

        String finalResult = finalResultFuture.get(200, TimeUnit.SECONDS);
        TaskResult callbackResult = callbackFuture.get(200, TimeUnit.SECONDS);
        finalResult = finalResult.replaceAll("\\s*\\(Task ID: [^)]+\\)", "").trim();
        String expectedResearcherOutput = "Researcher found: " + taskDesc;


        assertEquals(TaskStatus.COMPLETED, initialTask.getStatus());
        assertNotNull(callbackResult);
        assertEquals(TaskStatus.COMPLETED, callbackResult.status());
        String callbackOutput = callbackResult.output();
        callbackOutput = callbackOutput.replaceAll("\\s*\\(Task ID: [^)]+\\)", "").trim();
        assertNotNull(callbackOutput);
        String writerOutput = callbackOutput;

        List<String> logMessages = context.getLogHistory().stream().map(AgentContext.LogEntry::message).collect(Collectors.toList());
        assertTrue(logMessages.stream().anyMatch(msg -> msg.contains("Agent Researcher starting task: " + taskDesc)));

        assertTrue(logMessages.stream().anyMatch(msg -> msg.contains("Agent Writer starting task: " + writerOutput)));




        // Memory Assertions
        // Researcher should have stored its final output as a task summary.
        assertNotEquals(researcherAgent.getMemory().getAll().size(),0);
        assertTrue(((ShortTermMemory) researcherAgent.getMemory()).size() >0, "Researcher memory should not be empty.");

        // Writer's input task description is the researcher's output.
        assertTrue(((ShortTermMemory) writerAgent.getMemory()).size() >0, "Writer memory should not be empty.");
    }
}
