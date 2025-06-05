package com.javaagentai.aiagents.crew;

import com.javaagentai.aiagents.core.Agent;
import com.javaagentai.aiagents.core.AgentContext;
import com.javaagentai.aiagents.core.BasicAgent;
import com.javaagentai.aiagents.core.Crew;
import com.javaagentai.aiagents.core.ProcessStrategy;
import com.javaagentai.aiagents.core.Task;
import com.javaagentai.aiagents.llm.LLMClient;
import com.javaagentai.aiagents.memory.Memory;
import com.javaagentai.aiagents.tools.Tool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CrewEndToEndTest {

    private Crew crew;
    private List<Agent> agents;
    private ProcessStrategy strategy;

    @BeforeEach
    public void setup() {
        agents = new ArrayList<>();
        // Create a mock LLM client
        LLMClient llmClient = mock(LLMClient.class);
        // Create a mock memory
        Memory memory = mock(Memory.class);
        // Create a test agent
        BasicAgent agent = new BasicAgent("TestAgent", "TestRole", List.of(), llmClient, memory);
        agents.add(agent);
        strategy = ProcessStrategy.SEQUENTIAL;
    }

    @Test
    public void testCrewExecutionSequential() throws InterruptedException, ExecutionException {
        // Create a task
        Map<String, Object> input = new HashMap<>();
        input.put("key", "value");
        Task task = new Task("Test Task", input, "Expected Output");
        // Create a crew
        crew = new Crew(agents, strategy);
        // Execute the task
        CompletableFuture<String> result = crew.execute(task);
        // Verify the result
        String finalResult = result.get();
        assertNotNull(finalResult);
    }

    @Test
    public void testCrewExecutionHierarchical() throws InterruptedException, ExecutionException {
        // Create a task
        Map<String, Object> input = new HashMap<>();
        input.put("key", "value");
        Task task = new Task("Test Task", input, "Expected Output");
        // Create a crew
        crew = new Crew(agents, ProcessStrategy.HIERARCHICAL);
        // Execute the task
        CompletableFuture<String> result = crew.execute(task);
        // Verify the result
        String finalResult = result.get();
        assertNotNull(finalResult);
    }

    @Test
    public void testCrewExecutionWithHumanInput() throws InterruptedException, ExecutionException {
        // Create a task that requires human input
        Map<String, Object> input = new HashMap<>();
        input.put("key", "value");
        Task task = new Task("Test Task", input, "Expected Output", true);
        // Create a crew
        crew = new Crew(agents, strategy);
        // Execute the task
        CompletableFuture<String> result = crew.execute(task);
        // Simulate human input
        task.setHumanInput("Human Input");
        // Verify the result
        String finalResult = result.get();
        assertNotNull(finalResult);
    }


}