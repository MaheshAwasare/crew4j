package com.javaagentai.aiagents.crew;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.javaagentai.aiagents.core.Agent;
import com.javaagentai.aiagents.core.BasicAgent;
import com.javaagentai.aiagents.core.Crew;
import com.javaagentai.aiagents.core.ProcessStrategy;
import com.javaagentai.aiagents.core.Task;
import com.javaagentai.aiagents.llm.GroqClient;
import com.javaagentai.aiagents.memory.Memory;
import com.javaagentai.aiagents.memory.ShortTermMemory;
import com.javaagentai.aiagents.tools.PdfWriterTool;
import com.javaagentai.aiagents.tools.Tool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputFilter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class CrewIntegrationTest {

    private Crew crew;
    private List<Agent> agents;
    private ProcessStrategy strategy;

    @BeforeEach
    public void setup() throws IOException {
        agents = new ArrayList<>();
        String apiKey = System.getenv("GROQ_API_KEY");

        //String apiKey = config.getGroqApiKey();
       // GroqClient groqClient = new GroqClient(apiKey);
        // Create a GroqClient instance
        GroqClient groqClient = new GroqClient(apiKey, "meta-llama/llama-4-scout-17b-16e-instruct");
        // Create a Memory instance
        Memory memory = new ShortTermMemory(1000);

        // Create a Researcher agent
        BasicAgent researcher = new BasicAgent("Researcher", "Research", List.of(), groqClient, memory);
        agents.add(researcher);

        // Create a Writer agent
        BasicAgent writer = new BasicAgent("Writer", "Writing", List.of(new PdfWriterTool()), groqClient, memory);
        agents.add(writer);

        strategy = ProcessStrategy.SEQUENTIAL;
    }

    @Test
    public void testCrewExecutionSequential() throws InterruptedException, ExecutionException {
        // Create a task
        Map<String, Object> input = new HashMap<>();
        input.put("topic", "AI Applications");
        Task task = new Task("Summarize AI applications in a short article", input, "A short article on AI applications");

        // Create a crew
        crew = new Crew(agents, strategy);

        // Execute the task
        CompletableFuture<String> result = crew.execute(task);


                // Verify the result
        String finalResult = result.get();
        assertNotNull(finalResult);
        System.out.println("Researcher and Writer output: " + finalResult);
    }

    /*@Test
    public void testCrewExecutionHierarchical() throws InterruptedException, ExecutionException {
        // Create a task
        Map<String, Object> input = new HashMap<>();
        input.put("topic", "Future of AI");
        Task task = new Task("Write a comprehensive article on the future of AI", input, "A comprehensive article on AI future");

        // Create a crew
        crew = new Crew(agents, ProcessStrategy.HIERARCHICAL);

        // Execute the task
        CompletableFuture<String> result = crew.execute(task);

        // Verify the result
        String finalResult = result.get();
        assertNotNull(finalResult);
        System.out.println("Researcher and Writer hierarchical output: " + finalResult);
    }

    @Test
    public void testCrewExecutionWithHumanInput() throws InterruptedException, ExecutionException {
        // Create a task that requires human input
        Map<String, Object> input = new HashMap<>();
        input.put("topic", "Explain AI to a 10-year-old");
        Task task = new Task("Explain AI to a 10-year-old", input, "A simple explanation of AI", true);

        // Create a crew
        crew = new Crew(agents, strategy);

        // Execute the task
        CompletableFuture<String> result = crew.execute(task);

        // Simulate human input
        task.setHumanInput("AI is like a smart computer that can learn and help us");

        // Verify the result
        String finalResult = result.get();
        assertNotNull(finalResult);
        System.out.println("Researcher and Writer output with human input: " + finalResult);
    }*/
}