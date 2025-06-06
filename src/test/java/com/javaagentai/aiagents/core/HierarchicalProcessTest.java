package com.javaagentai.aiagents.core;

import com.javaagentai.aiagents.llm.LLMClient;
import com.javaagentai.aiagents.memory.Memory;
import com.javaagentai.aiagents.memory.ShortTermMemory;
import com.javaagentai.aiagents.tools.ExampleEchoTool;
import com.javaagentai.aiagents.tools.Tool;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

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
public class HierarchicalProcessTest {

    static final String RESEARCHER_NAME = "ResearcherAgent";
    static final String WRITER_NAME = "WriterAgent";
    static final String MANAGER_NAME = "ProjectManager";

    static final String SUBTASK_RESEARCH_DESC = "Research the impact of AI on climate change.";
    static final String SUBTASK_WRITE_DESC = "Write a blog post about AI's role in climate change based on research.";
    static final String MANAGER_NOTES = "Ensure the blog post is optimistic and highlights solutions.";

    static final String MOCK_RESEARCH_RESULT = "AI can optimize energy grids and predict climate patterns.";
    static final String MOCK_WRITE_RESULT = "Blog post: AI is a key ally in tackling climate change by optimizing energy and predicting patterns. It offers hope for a sustainable future.";


    static class MockManagerLLMClient implements LLMClient {
        private final String initialTaskDescForPlan;

        public MockManagerLLMClient(String initialTaskDescForPlan) {
            this.initialTaskDescForPlan = initialTaskDescForPlan;
        }

        @Override
        public String complete(String prompt) {
            // Check if this is the planning phase or synthesis phase
            if (prompt.contains(this.initialTaskDescForPlan) && !prompt.contains("Synthesize the final answer")) {
                // Planning phase: return the JSON plan
                // Ensure the assigned_agent_name matches the static names
                return String.format("""
                    {
                      "sub_tasks": [
                        {
                          "task_description": "%s",
                          "assigned_agent_name": "%s",
                          "expected_output": "A summary of research findings on AI and climate change."
                        },
                        {
                          "task_description": "%s",
                          "assigned_agent_name": "%s",
                          "expected_output": "A 300-word blog post."
                        }
                      ],
                      "manager_notes": "%s"
                    }
                    """, SUBTASK_RESEARCH_DESC, RESEARCHER_NAME, SUBTASK_WRITE_DESC, WRITER_NAME, MANAGER_NOTES);
            } else if (prompt.contains("Synthesize the final answer")) {
                // Synthesis phase: combine results
                // A real LLM would do this more intelligently. We'll just mock a combination.
                String researchResult = extractFromResultString(prompt, SUBTASK_RESEARCH_DESC);
                String writeResult = extractFromResultString(prompt, SUBTASK_WRITE_DESC);
                return "Final Synthesized Report: Research found that " + researchResult + ". The blog post produced is: " + writeResult + ". Notes: " + MANAGER_NOTES;
            }
            return "Error: MockManagerLLMClient could not determine phase.";
        }

        private String extractFromResultString(String prompt, String subTaskDesc) {
            String key = "- Sub-task: " + subTaskDesc + "\n  Result: ";
            int startIndex = prompt.indexOf(key);
            if (startIndex == -1) return "Error: Sub-task result not found in prompt for '" + subTaskDesc + "'";
            startIndex += key.length();
            int endIndex = prompt.indexOf("\n", startIndex);
            if (endIndex == -1) endIndex = prompt.length();
            return prompt.substring(startIndex, endIndex).trim();
        }



        public String complete(String prompt, Map<String, Object> options) {
            return complete(prompt);
        }

        @Override
        public void close() {}
    }

    static class MockWorkerLLMClient implements LLMClient {
        private final String agentName;

        public MockWorkerLLMClient(String agentName) {
            this.agentName = agentName;
        }

        @Override
        public String complete(String prompt) {
            if (RESEARCHER_NAME.equals(agentName)) {
                // Check if prompt contains SUBTASK_RESEARCH_DESC
                 if (prompt.contains(SUBTASK_RESEARCH_DESC)) {
                    return MOCK_RESEARCH_RESULT;
                }
            } else if (WRITER_NAME.equals(agentName)) {
                // Check if prompt contains SUBTASK_WRITE_DESC
                // A more advanced check would see if MOCK_RESEARCH_RESULT is also in the prompt for the writer
                if (prompt.contains(SUBTASK_WRITE_DESC)) {
                    return MOCK_WRITE_RESULT;
                }
            }
            return "Error: MockWorkerLLMClient for " + agentName + " received unexpected prompt.";
        }


        public String complete(String prompt, Map<String, Object> options) {
            return complete(prompt);
        }

        @Override
        public void close() {}
    }

    private static BasicAgent projectManagerAgent;
    private static BasicAgent researcherAgent;
    private static BasicAgent writerAgent;
    private static Memory managerMemory, researcherMemory, writerMemory;

    @BeforeAll
    void setUpAll() {
        managerMemory = new ShortTermMemory();
        researcherMemory = new ShortTermMemory();
        writerMemory = new ShortTermMemory();
        
        // No tools for this basic hierarchical test to keep it focused on the process itself.

        projectManagerAgent = BasicAgent.builder()
                .name(MANAGER_NAME)
                .role("Project Management")
                .tools(Collections.emptyList())
                .llmClient(new MockManagerLLMClient("Create a blog post about AI and climate change, including research."))
                .memory(managerMemory)
                .build();

        researcherAgent = BasicAgent.builder()
                .name(RESEARCHER_NAME)
                .role("Research Specialist")
                .tools(Collections.emptyList())
                .llmClient(new MockWorkerLLMClient(RESEARCHER_NAME))
                .memory(researcherMemory)
                .build();

        writerAgent = BasicAgent.builder()
                .name(WRITER_NAME)
                .role("Content Writing Specialist")
                .tools(Collections.emptyList())
                .llmClient(new MockWorkerLLMClient(WRITER_NAME))
                .memory(writerMemory)
                .build();

    }

    @AfterAll
    void tearDownAll() {
        if (projectManagerAgent != null) projectManagerAgent.shutdown();
        if (researcherAgent != null) researcherAgent.shutdown();
        if (writerAgent != null) writerAgent.shutdown();
    }

    @Test
    void testHierarchicalExecutionFlow() throws InterruptedException, ExecutionException, TimeoutException {
        AgentContext context = new AgentContext();
        List<Agent> agents = List.of(projectManagerAgent, researcherAgent, writerAgent); // Manager first

        String initialTaskDesc = "Create a blog post about AI and climate change, including research.";
        CompletableFuture<TaskResult> callbackFuture = new CompletableFuture<>();
        Task initialTask = Task.builder()
                .description(initialTaskDesc)
                .input(Map.of("topic", "AI and Climate Change"))
                .expectedOutput("A comprehensive blog post on AI's role in climate change, based on research.")
                .status(TaskStatus.PENDING)
                .callback(result -> {
                    context.log("HIERARCHICAL_TEST_CALLBACK: Task " + result.status() +
                            (result.error() != null ? " Error: " + result.error() : " Output: " + result.output()));
                    callbackFuture.complete(result);
                })
                .build();


        HierarchicalProcess hierarchicalProcess = new HierarchicalProcess();
        CompletableFuture<String> finalResultFuture = hierarchicalProcess.execute(initialTask, agents, context);

        // Assertions
        String finalResult = finalResultFuture.get(30, TimeUnit.SECONDS); // Increased timeout for complex flow
        TaskResult callbackResult = callbackFuture.get(30, TimeUnit.SECONDS);

        String expectedFinalSynthesizedOutput = "Final Synthesized Report: Research found that " + MOCK_RESEARCH_RESULT + ". The blog post produced is: " + MOCK_WRITE_RESULT + ". Notes: " + MANAGER_NOTES;
        assertEquals(expectedFinalSynthesizedOutput, finalResult, "Final result from process should match expected synthesized output.");

        assertEquals(TaskStatus.COMPLETED, initialTask.getStatus(), "Initial task status should be COMPLETED.");
        assertNotNull(callbackResult, "Callback should have been triggered.");
        assertEquals(TaskStatus.COMPLETED, callbackResult.status(), "Callback result status should be COMPLETED.");
        assertEquals(expectedFinalSynthesizedOutput, callbackResult.output(), "Callback output should match the final synthesized output.");

        // Verify logs
        List<String> logMessages = context.getLogHistory().stream().map(AgentContext.LogEntry::message).collect(Collectors.toList());
        assertTrue(logMessages.stream().anyMatch(msg -> msg.contains("HIERARCHICAL_PROCESS: Asking manager " + MANAGER_NAME + " to plan sub-tasks.")), "Manager planning log missing.");
        assertTrue(logMessages.stream().anyMatch(msg -> msg.contains("HIERARCHICAL_PROCESS: Assigning sub-task '" + SUBTASK_RESEARCH_DESC + "' to agent " + RESEARCHER_NAME)), "Researcher assignment log missing.");
        assertTrue(logMessages.stream().anyMatch(msg -> msg.contains("HIERARCHICAL_PROCESS: Sub-task '" + SUBTASK_RESEARCH_DESC + "' completed by " + RESEARCHER_NAME)), "Researcher completion log missing.");
        assertTrue(logMessages.stream().anyMatch(msg -> msg.contains("HIERARCHICAL_PROCESS: Assigning sub-task '" + SUBTASK_WRITE_DESC + "' to agent " + WRITER_NAME)), "Writer assignment log missing.");
        assertTrue(logMessages.stream().anyMatch(msg -> msg.contains("HIERARCHICAL_PROCESS: Sub-task '" + SUBTASK_WRITE_DESC + "' completed by " + WRITER_NAME)), "Writer completion log missing.");
        assertTrue(logMessages.stream().anyMatch(msg -> msg.contains("HIERARCHICAL_PROCESS: Asking manager " + MANAGER_NAME + " to synthesize final answer.")), "Manager synthesis log missing.");

        // Verify memories
        // Manager memory should contain the initial plan and the final synthesis.
        // For simplicity, we'll check if it's not empty and contains some expected snippet.
        // BasicAgent stores "task_summary:" + task.getDescription() for final answers.
        // The manager is called twice with different task descriptions for planning and synthesis.
        assertTrue(((ShortTermMemory)projectManagerAgent.getMemory()).size() >= 1, "Manager memory should not be empty."); 
        // Example check: The plan itself is not directly stored by BasicAgent's current memory logic.
        // BasicAgent stores the *output* of its `performTask`. So for planning, it stores the JSON plan.
        // For synthesis, it stores the final synthesized report.
        assertNotNull(projectManagerAgent.getMemory().get("task_summary:" + initialTaskDesc), "Manager should have stored its planning output (the JSON plan).");
        
        String synthesisTaskDescStart = "Original Task: " + initialTaskDesc; // Manager's synthesis task description starts with this.
        // We need to find the exact description key for synthesis task summary.
        // This is tricky as the full desc is long. We'll check if *any* summary matches the expected final output.
        boolean synthesisStored = projectManagerAgent.getMemory().getAll().stream()
                                    .anyMatch(val -> val instanceof String && ((String)val).equals(expectedFinalSynthesizedOutput));
        assertTrue(synthesisStored, "Manager should have stored the final synthesized report in its memory.");


        assertNotNull(researcherAgent.getMemory().get("task_summary:" + SUBTASK_RESEARCH_DESC), "Researcher memory should contain summary of its work.");
        assertEquals(MOCK_RESEARCH_RESULT, researcherAgent.getMemory().get("task_summary:" + SUBTASK_RESEARCH_DESC));

        assertNotNull(writerAgent.getMemory().get("task_summary:" + SUBTASK_WRITE_DESC), "Writer memory should contain summary of its work.");
        assertEquals(MOCK_WRITE_RESULT, writerAgent.getMemory().get("task_summary:" + SUBTASK_WRITE_DESC));
    }
}
