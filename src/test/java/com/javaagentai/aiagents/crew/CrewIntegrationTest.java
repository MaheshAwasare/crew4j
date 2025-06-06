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
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Author: Mahesh Awasare 
 */
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
        Task criticalTask = Task.builder()
                .description("Generate a marketing strategy")
                .input(new HashMap<>())
                .expectedOutput("Comprehensive marketing strategy")
                .requiresHumanInput(true)  // Enable human review
                .build();
        // Create a Researcher agent

        Agent researcher = BasicAgent.builder()
                .role("Research")
                .name("Researcher")
                .tools(Collections.emptyList())
                .llmClient(groqClient)
                .memory(memory)
                .build();

        agents.add(researcher);

        // Create a Writer agent


        Agent writer = BasicAgent.builder()
                .name("Writer")
                .role("Writing")
                .llmClient(groqClient)
                .memory(memory)
                .tools(List.of(new PdfWriterTool()))
                .build();
        agents.add(writer);

        strategy = ProcessStrategy.SEQUENTIAL;
    }

    @Test
    public void testCrewExecutionSequential() throws InterruptedException, ExecutionException {
        // Create a task
        Map<String, Object> input = new HashMap<>();
        input.put("topic", "AI Applications");
        Task task = Task.builder()
                .description("Summarize AI applications in a short article")
                .input(input)

                .expectedOutput("A short article on AI applications")
                .build();


        // Create a crew

        crew = Crew.builder().agents(agents).processStrategy(ProcessStrategy.SEQUENTIAL).build();


        // Execute the task
        CompletableFuture<String> result = crew.execute(task);


                // Verify the result
        String finalResult = result.get();
        assertNotNull(finalResult);
        System.out.println("Researcher and Writer output: " + finalResult);
    }

   
}