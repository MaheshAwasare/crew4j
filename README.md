# Crew4J - Java-based AI Agent Orchestration Framework

## 1. Introduction/Overview

Crew4J is a powerful and flexible Java-based framework designed for building and managing collaborative multi-agent AI systems. Inspired by frameworks like CrewAI, this project provides a robust platform for orchestrating AI agents that can perform complex tasks through collaboration. It enables developers to define agents with specific roles, tools, and memory, assign them tasks, and manage their execution through various process strategies. The framework is built with asynchronous operations at its core, ensuring efficient and non-blocking execution.

## 2. Features

- **Agent Creation:** Define specialized agents with unique roles, assignable tools, and dedicated memory systems
- **Task Definition:** Create detailed tasks with descriptions, inputs, expected outputs, and completion callbacks
- **Tool Integration:** Equip agents with tools for web search, code execution, data analysis, and custom capabilities
- **Crew Orchestration:** Form crews of agents to collaborate on complex tasks
- **Process Strategies:**
  - **Sequential:** Agents execute tasks one after another in a defined sequence
  - **Hierarchical:** A manager agent delegates sub-tasks to worker agents and synthesizes results
  - **Consensual:** Multiple agents perform the same task independently, with outputs synthesized for consensus
- **Memory Management:** Short-term memory for contextual awareness, with long-term memory planned
- **Human-in-the-Loop (HITL):** Tasks can require human input for critical decision points
- **Asynchronous Operations:** Non-blocking execution using `CompletableFuture` for optimal performance

## 3. Core Concepts

### Agent (`com.javaagentai.aiagents.core.Agent`)
An interface defining autonomous entities capable of performing tasks. The `BasicAgent` implementation provides:
- Customizable roles and descriptions
- Integration with language model clients
- Tool usage capabilities
- Memory systems for information storage

### Task (`com.javaagentai.aiagents.core.Task`)
Represents work to be executed, including:
- Description and input parameters
- Expected output specifications
- Optional human-in-the-loop callbacks
- Status tracking (`PENDING`, `IN_PROGRESS`, `AWAITING_HUMAN_INPUT`, `COMPLETED`, `FAILED`)

### Tool (`com.javaagentai.aiagents.tools.Tool`)
Capabilities that extend agent functionality beyond language generation:
- Web search and information retrieval
- Code execution capabilities
- Data analysis and processing
- Custom tool development framework

### Memory (`com.javaagentai.aiagents.memory.Memory`)
Information storage and retrieval system:
- **ShortTermMemory:** Session-based storage for current execution context
- **LongTermMemory:** Planned for persistent storage across sessions

### AgentContext (`com.javaagentai.aiagents.core.AgentContext`)
Shared environment providing:
- Common resources and shared data
- Task-scoped data storage
- Logging facilities for coordination

### Crew (`com.javaagentai.aiagents.core.Crew`)
Groups of agents working together with defined process strategies for task coordination and result synthesis.

### Process (`com.javaagentai.aiagents.core.Process`)
Orchestration logic determining how agents collaborate:
- **SequentialProcess:** Linear workflow with clear dependencies
- **HierarchicalProcess:** Manager-worker delegation pattern
- **ConsensualProcess:** Parallel execution with consensus building

## 4. Getting Started

### Maven Dependency

Add the following dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>com.crew4j</groupId>
    <artifactId>jcrew</artifactId>
    <version>1.0.1</version>
</dependency>
```

Include Jackson for JSON processing:

```xml
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
    <version>2.15.2</version>
</dependency>
```

### Basic Example: Creating an Agent

```java
import com.javaagentai.aiagents.core.Agent;
import com.javaagentai.aiagents.core.BasicAgent;
import com.javaagentai.aiagents.llm.LLMClient;

// Create a mock LLM client for this example
LLMClient llmClient = prompt -> {
    // In a real scenario, this would call an actual LLM API
    return "This is a response from the language model for prompt: " + prompt;
};

// Create a basic agent with a role and LLM client
Agent researcher = BasicAgent.builder()
    .role("Research Specialist")
    .LLMClient(llmClient)
    .build();
```

### Basic Example: Defining a Task

```java
import com.javaagentai.aiagents.core.Task;

// Define a task with description, input, and expected output
Task researchTask = Task.builder()
    .description("Research the latest developments in AI agents")
    .input("Focus on multi-agent systems and their applications")
    .expectedOutput("A summary of key findings and trends")
    .build();
```

### Basic Example: Running a Simple Crew

```java
import com.javaagentai.aiagents.core.Crew;
import com.javaagentai.aiagents.core.process.SequentialProcess;

// Create a crew with a sequential process
Crew researchCrew = Crew.builder()
    .agents(List.of(researcher,writer))
    
    .withProcess(new SequentialProcess())
    .build();

// Run the crew with the task and get the result
CompletableFuture<String> result = researchCrew.run(researchTask);

// When the task completes, process the result
result.thenAccept(output -> {
    System.out.println("Task completed with result: " + output);
});
```

## 5. Advanced Examples

### Hierarchical Process

The hierarchical process designates a manager agent that delegates subtasks to worker agents and synthesizes their outputs.

```java
// Define a manager agent with specific LLM configuration
Agent manager = BasicAgent.builder()
    .role("Project Manager")
    .llmClient(new OpenAiClient("apikey", "gpt-4.0"))
    .build();

// Define worker agents
Agent researcher = BasicAgent.builder()
    .role("Research Specialist")
    .llmClient(llmClient)
    .build();

Agent writer = BasicAgent.builder()
    .role("Content Writer")
    .llmClient(llmClient)
    .build();

// Create a hierarchical process crew
Crew researchTeam = Crew.builder()
    .agents(List.of(manager, researcher, writer))
    .process(new HierarchicalProcess(manager))
    .build();

// Run the crew with a complex task
CompletableFuture<String> result = researchTeam.run(complexTask);
```

**How It Works:**
1. The manager agent breaks down the main task into subtasks
2. Worker agents execute their assigned subtasks
3. The manager collects and synthesizes all results
4. The final output combines the work of all agents

### Consensual Process

The consensual process has multiple agents perform the same task independently, and their outputs are synthesized to form a consensus.

```java
// Define multiple expert agents
Agent expertA = BasicAgent.builder()
    .role("AI Ethics Expert")
    .llmClient(llmClient)
    .build();

Agent expertB = BasicAgent.builder()
    .role("AI Technical Expert")
    .llmClient(llmClient)
    .build();

Agent expertC = BasicAgent.builder()
    .role("AI Policy Expert")
    .llmClient(llmClient)
    .build();

// Create a consensual process crew
Crew expertPanel = Crew.builder()
    .agents(List.of(expertA, expertB, expertC))
    .process(new ConsensualProcess())
    .build();

// Run the crew to get consensus on a complex question
CompletableFuture<String> consensus = expertPanel.run(ethicsQuestion);
```

**When to Use:**
- For critical decisions requiring multiple perspectives
- When validation and fact-checking are important
- To reduce bias by combining diverse viewpoints
- For complex problems where consensus improves accuracy

### Tool Usage

Tools extend agent capabilities beyond language generation, allowing them to perform actions like web searches, code execution, and data analysis.

```java
// Create a web search tool
Tool webSearchTool = new WebSearchTool();

// Create a code execution tool
Tool codeExecutionTool = new CodeExecutionTool();

// Add tools to an agent
Agent researchAgent = BasicAgent.builder()
    .role("Research Assistant")
    .llmClient(llmClient)
    .build();

researchAgent.addTool(webSearchTool);
researchAgent.addTool(codeExecutionTool);

// The agent can now use these tools during task execution
// LLM will be prompted with tool descriptions and usage patterns
```

**Tool Integration Process:**
1. Create tool implementations for specific functionalities
2. Add tools to agents that need those capabilities
3. During task execution, the LLM is informed about available tools
4. The agent invokes tools when needed to complete tasks

### Memory Usage

Memory allows agents to store and retrieve information across interactions, maintaining context and learning from past experiences.

```java
// Create an agent with memory
Agent agent = BasicAgent.builder()
    .role("Assistant")
    .llmClient(llmClient)
    .memory(new ShortTermMemory())
    .build();

// Agent stores information in memory
agent.getMemory().store("user_preference", "prefers detailed explanations");

// Later, the agent can retrieve this information
String preference = agent.getMemory().retrieve("user_preference");

// The memory is also provided to the LLM during task execution
// so the model can reference past interactions and stored information
```

**Memory Types:**
- **ShortTermMemory:** For session-based storage, retaining information during the current execution
- **LongTermMemory:** Coming soon - Persistent storage for information across multiple sessions

### Human-in-the-Loop (HITL)

Human-in-the-Loop allows for human intervention at critical points in the agent workflow, enabling review, correction, and guidance.

```java
// Define a task with human-in-the-loop
Task criticalTask = Task.builder()
    .description("Generate a marketing strategy")
    .input(new HashMap<>())
    .expectedOutput("Comprehensive marketing strategy")
    .requiresHumanInput(true)  // Enable human review
    .build();

// Run the task with an agent
CompletableFuture<String> result = marketingAgent.executeTask(criticalTask);

// The task execution will pause at the defined checkpoint
// and wait for human input before continuing

// To provide human input (in your application code):
criticalTask.setHumanInput("Please focus more on social media channels");
```

**When to Use HITL:**
- For sensitive decisions requiring human judgment
- When quality assurance is critical
- For processes that may require course correction
- To provide additional context or instructions

**HITL Process Flow:**
1. Define a task with human-in-the-loop enabled
2. Agent begins task execution
3. At the checkpoint, execution pauses
4. System waits for human input
5. Human provides feedback or guidance
6. Agent continues execution with human input

## 6. LLM Integration

Crew4J integrates with Large Language Models through the `LLMClient` interface, allowing connection to any LLM provider.

### LLMClient Interface

```java
public interface LLMClient {
    /**
     * Completes the given prompt using the language model.
     *
     * @param prompt The input prompt to be completed
     * @return The completion result from the language model
     */
    String complete(String prompt);
}
```

### OpenAI Integration Example

```java
package com.javaagentai.aiagents.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

public class OpenAiClient implements LLMClient {
    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final ObjectMapper mapper;

    public OpenAiClient(String apiKey, String model) {
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = "https://api.openai.com/v1/chat/completions";
        this.mapper = new ObjectMapper();
    }

    @Override
    public String complete(String prompt) {
        try {
            Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(Map.of(
                    "role", "user",
                    "content", prompt
                ))
            );

            String requestBody = mapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

            HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return "[OpenAI error: " + response.statusCode() + "]";
            }

            Map<?, ?> json = mapper.readValue(response.body(), Map.class);
            List<?> choices = (List<?>) json.get("choices");

            if (choices != null && !choices.isEmpty()) {
                Map<?, ?> choice = (Map<?, ?>) choices.get(0);
                Map<?, ?> message = (Map<?, ?>) choice.get("message");
                return (String) message.get("content");
            }

            return "[OpenAI error: unable to retrieve response]";

        } catch (Exception e) {
            return "[OpenAI error: " + e.getMessage() + "]";
        }
    }

    @Override
    public void close() {
        // No resources to close in this implementation
    }
}
```

### Supported LLM Providers

Crew4J can integrate with any LLM provider that offers an API:

- **OpenAI:** GPT-3.5, GPT-4, and future models
- **Google:** PaLM, Gemini, and other models via Vertex AI
- **Anthropic:** Claude models via the Anthropic API
- **Groq:** Fast inference for various models
- **Custom LLMs:** Self-hosted open-source models, internal proprietary models, or any other LLM service

### LLM Integration Best Practices

**Performance Optimization:**
- Use connection pooling to reduce latency
- Implement retries with backoff for transient failures
- Cache responses when appropriate
- Use appropriate timeouts

**Error Handling:**
- Catch and log specific exceptions
- Implement graceful fallbacks
- Validate responses for expected format and quality
- Monitor rate limits

**Security Considerations:**
- Secure API key storage using environment variables
- Use HTTPS for all API communication
- Consider privacy implications of data sent to LLMs
- Validate and sanitize inputs to prevent prompt injection

## 7. Architecture and Design

### Asynchronous Operations

Crew4J leverages `CompletableFuture` for non-blocking execution:

```java
// All agent operations return CompletableFuture
CompletableFuture<String> agentResult = agent.executeTask(task);

// Chain operations asynchronously
agentResult
    .thenApply(result -> processResult(result))
    .thenAccept(finalResult -> saveToDatabase(finalResult))
    .exceptionally(throwable -> {
        logger.error("Task failed", throwable);
        return null;
    });
```

### Memory Architecture

The memory system provides contextual awareness:

```java
// Memory automatically integrates with LLM prompts
Memory memory = new ShortTermMemory();
memory.store("user_context", "Working on quarterly report");
memory.store("previous_result", "Market analysis completed");

// During task execution, relevant memory is included in prompts
String relevantInfo = memory.search(task.getDescription(), 3);
// This information is automatically added to LLM prompts
```

## 8. Contributing

Contributions are welcome! Please refer to the contribution guidelines for:
- Coding standards and best practices
- Testing requirements and procedures
- Pull request processes
- Issue reporting guidelines

## 9. License

This project is licensed under the **MIT License**. See the `LICENSE` file for details.

## 10. Support and Community

- **GitHub Repository:** [https://github.com/MaheshAwasare/crew4j](https://github.com/MaheshAwasare/crew4j)
- **Documentation:** [https://crew4j.com](https://crew4j.com)
- **Issues:** Report bugs and feature requests on GitHub
- **Email:** maheshawasare@gmail.com

---

**Author:** Mahesh Awasare

Crew4J empowers Java developers to build sophisticated multi-agent AI systems with ease, providing the tools and flexibility needed to create collaborative AI solutions for complex real-world problems.
