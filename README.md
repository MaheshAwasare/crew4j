# Crw4J Java based AI Agent Orchestration Framework

## 1. Introduction/Overview

The Crw4J Java based AI Agent Orchestration Framework is a powerful and flexible library designed for building and managing multi-agent AI systems in Java. Inspired by the capabilities of frameworks like CrewAI, this project provides a robust platform for orchestrating collaborative AI agents that can perform complex tasks. It enables developers to define agents with specific roles, tools, and memory, assign them tasks, and manage their execution through various process strategies. The framework is built with asynchronous operations at its core, ensuring efficient and non-blocking execution.

## 2. Features

*   **Agent Creation:** Define specialized agents with unique roles, assignable tools, and dedicated memory.
*   **Task Definition:** Create detailed tasks with descriptions, inputs, expected outputs, and completion callbacks.
*   **Tool Integration:** Equip agents with tools that they can leverage to perform actions or gather information.
*   **Crew Orchestration:** Form crews of agents to collaborate on tasks.
*   **Process Strategies:**
    *   **Sequential:** Agents execute tasks one after another, passing output to the next.
    *   **Hierarchical:** A manager agent breaks down tasks and delegates sub-tasks to worker agents, then synthesizes the final result.
    *   **Consensual:** Multiple agents perform the same task, and their outputs are synthesized by a designated agent to form a consensus.
*   **Memory Management:** Agents are equipped with short-term memory to retain information across interactions.
*   **Human-in-the-Loop (HITL):** Tasks can be configured to require human input, allowing for manual review and intervention before an agent proceeds.
*   **Asynchronous Operations:** Utilizes `CompletableFuture` for non-blocking task execution and agent interactions, enabling efficient resource utilization.

## 3. Core Concepts

*   **`Agent` (`com.javaagentai.aiagents.core.Agent`)**:
    *   An interface defining the contract for an autonomous entity capable of performing tasks.
    *   **`BasicAgent`**: The primary implementation, allowing configuration of name, role, tools, an LLM client, and memory. It can perform tasks by interacting with an LLM and using its assigned tools.

*   **`Task` (`com.javaagentai.aiagents.core.Task`)**:
    *   Represents a piece of work to be done. It includes a description, input parameters, expected output, an optional callback for completion/failure, a unique ID, and status.
    *   Tasks can be marked as requiring human input, pausing execution until the input is provided.
    *   Lifecycle statuses include `PENDING`, `IN_PROGRESS`, `AWAITING_HUMAN_INPUT`, `COMPLETED`, and `FAILED`.

*   **`Tool` (`com.javaagentai.aiagents.tools.Tool`)**:
    *   An interface for capabilities that an agent can use (e.g., web search, code execution, database query).
    *   Agents, particularly `BasicAgent`, are designed to understand when and how to use their tools based on LLM interactions. The `use` method returns a `CompletableFuture<String>`.

*   **`Memory` (`com.javaagentai.aiagents.memory.Memory`)**:
    *   An interface defining how agents store and retrieve information.
    *   **`ShortTermMemory`**: An in-memory implementation allowing agents to remember recent interactions, task details, or tool outputs. It supports adding, getting, and searching for data.
    *   `LongTermMemory` is planned for more persistent storage.

*   **`AgentContext` (`com.javaagentai.aiagents.core.AgentContext`)**:
    *   Provides a shared environment for agents within a process. It includes:
        *   **Shared Data:** A map for agents to share information.
        *   **Task-Scoped Data:** Storage specific to a task ID.
        *   **Logging:** A mechanism to log messages with timestamps, providing a history of operations.

*   **`Crew` (`com.javaagentai.aiagents.core.Crew`)**:
    *   Represents a group of agents assembled to accomplish a larger objective.
    *   It is configured with a list of agents and a `ProcessStrategy`.
    *   The `execute(Task initialTask)` method initiates the work, delegating to the chosen `Process`.

*   **`Process` (`com.javaagentai.aiagents.core.Process`)**:
    *   An interface defining the orchestration logic for how a `Crew` executes tasks.
    *   Implementations include:
        *   **`SequentialProcess`**: Tasks are executed by agents in the order they are provided in the crew. The output of one agent can become the input for the next.
        *   **`HierarchicalProcess`**: The first agent in the crew acts as a manager, breaking down the initial task into sub-tasks and assigning them to other "worker" agents. The manager then synthesizes the results.
        *   **`ConsensualProcess`**: All agents in the crew (including the last one) perform the initial task in parallel. The last agent then acts as a synthesizer, taking all individual outputs to produce a final, consolidated answer.

## 4. Getting Started / Usage

### Maven Dependency

To include this framework in your project, add the following Maven dependency (update with actual group ID, artifact ID, and version):

```xml
<dependency>
    <groupId>com.crew4j</groupId>
    <artifactId>jcrew</artifactId>
    <version>0.1.0-SNAPSHOT</version> <!-- Or the latest version -->
</dependency>
```

You will also need to include dependencies for your chosen LLM client (e.g., Vertex AI, OpenAI) and Jackson for JSON processing if using features like `BasicAgent`'s tool calling or `HierarchicalProcess`.

```xml
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
    <version>2.15.0</version> <!-- Use a recent version -->
</dependency>
```

### Basic Example: Creating an Agent

```java
import com.javaagentai.aiagents.core.Agent;
import com.javaagentai.aiagents.core.BasicAgent;
import com.javaagentai.aiagents.llm.LLMClient; // Your LLM client implementation
import com.javaagentai.aiagents.memory.Memory;
import com.javaagentai.aiagents.memory.ShortTermMemory;
import com.javaagentai.aiagents.tools.Tool; // Your tool implementation
// Assuming ExampleEchoTool and a MockLLMClient for simplicity
import com.javaagentai.aiagents.tools.ExampleEchoTool; 
// import com.javaagentai.aiagents.llm.MockLLMClient; // If you have a mock

import java.util.List;
import java.util.Map;

// LLMClient client = new YourLLMClientImplementation(...);
// For example purposes, let's assume a simple mock or null if not directly testing LLM interaction
LLMClient client = null; // Replace with actual LLM client
Tool tool = new ExampleEchoTool();
Memory memory = new ShortTermMemory();

Agent agent = new BasicAgent(
    "ResearcherAgent",
    "Specialized in researching complex topics.",
    List.of(tool),
    client,
    memory
);

System.out.println("Agent created: " + agent.getName());
```

### Basic Example: Defining a Task

```java
import com.javaagentai.aiagents.core.Task;
import java.util.Map;

Task task = new Task(
    "Research the future of renewable energy.",
    Map.of("specific_focus", "solar and wind power"),
    "A detailed report on the future trends of solar and wind power.",
    false // requiresHumanInput = false by default in this constructor
);

System.out.println("Task defined: " + task.getDescription());
```

### Basic Example: Running a Simple Crew (Sequential)

```java
import com.javaagentai.aiagents.core.Crew;
import com.javaagentai.aiagents.core.ProcessStrategy;
import com.javaagentai.aiagents.core.Agent;
import com.javaagentai.aiagents.core.Task;
// Assume agent1, agent2, and initialTask are defined as above or similarly

// LLMClient client1 = ...; Memory memory1 = ...;
// Agent agent1 = new BasicAgent("Researcher", "...", List.of(), client1, memory1);
// LLMClient client2 = ...; Memory memory2 = ...;
// Agent agent2 = new BasicAgent("Writer", "...", List.of(), client2, memory2);
// Task initialTask = new Task("Research and write about AI", Map.of(), "Article about AI");

// For this snippet, let's assume agent1 and agent2 are already created.
// For a runnable example, ensure they are properly initialized.
// Agent agent1 = new BasicAgent("Agent1", "Role1", List.of(), null, new ShortTermMemory());
// Agent agent2 = new BasicAgent("Agent2", "Role2", List.of(), null, new ShortTermMemory());
// Task initialTask = new Task("Sequential Task", Map.of(), "Sequential Output");


// Crew crew = new Crew(List.of(agent1, agent2), ProcessStrategy.SEQUENTIAL);
// CompletableFuture<String> resultFuture = crew.execute(initialTask);

// try {
//     String output = resultFuture.get(); // Blocks until the future is complete
//     System.out.println("Crew execution finished. Final Output: " + output);
// } catch (InterruptedException | ExecutionException e) {
//     e.printStackTrace();
// }
```
*(Note: The above crew example is conceptual. A runnable version requires actual LLM client mocks or implementations for agents to produce meaningful sequential output.)*

## 5. Detailed Usage Examples (Conceptual)

### Hierarchical Process

A `HierarchicalProcess` involves a manager agent delegating tasks to worker agents.

1.  **Define Agents:** Create a manager agent and one or more worker agents.
2.  **Configure Manager's LLM:** The manager's LLM should be prompted to understand its role: to break down the main task and assign sub-tasks to available workers in a specific JSON format.
    ```json
    // Expected JSON output from Manager for planning
    {
      "sub_tasks": [
        {
          "task_description": "Sub-task 1 description",
          "assigned_agent_name": "WorkerAgentName1",
          "expected_output": "Expected output for sub-task 1"
        }
      ],
      "manager_notes": "Optional notes for synthesis phase."
    }
    ```
3.  **Create Crew:** The first agent in the list passed to the `Crew` constructor becomes the manager.
    ```java
    // Agent manager = new BasicAgent("Manager", "Manages tasks", ..., managerLLM, managerMemory);
    // Agent worker1 = new BasicAgent("Worker1", "Executes sub-tasks", ..., workerLLM1, workerMemory1);
    // Crew crew = new Crew(List.of(manager, worker1), ProcessStrategy.HIERARCHICAL);
    // CompletableFuture<String> finalReport = crew.execute(mainTask);
    ```
    The `HierarchicalProcess` will first call the manager to get the plan, then execute sub-tasks sequentially using workers, and finally call the manager again to synthesize the results.

### Consensual Process

A `ConsensualProcess` has all agents perform the initial task, and then a synthesizer (last agent in the list) combines their outputs.

1.  **Define Agents:** Create multiple contributor agents and one synthesizer agent.
2.  **Create Crew:** List contributor agents first, with the synthesizer agent last.
    ```java
    // Agent contributor1 = ...; Agent contributor2 = ...; Agent synthesizer = ...;
    // Crew crew = new Crew(List.of(contributor1, contributor2, synthesizer), ProcessStrategy.CONSENSUAL);
    // CompletableFuture<String> consensusOutput = crew.execute(initialTask);
    ```
    The `ConsensualProcess` executes `initialTask` on `contributor1` and `contributor2` (and `synthesizer` too, as per current implementation) in parallel. Then, it provides all outputs to `synthesizer` to produce the final answer.

### Tool Usage

Agents equipped with tools can decide to use them based on LLM guidance. The `BasicAgent` is designed to prompt the LLM with available tools and parse a JSON response if the LLM indicates a tool should be used.

```java
// Conceptual LLM interaction for tool use (internal to BasicAgent):
// LLM might respond with:
// {
//   "tool_name": "WebSearchTool",
//   "tool_parameters": { "query": "latest AI trends" }
// }
// BasicAgent would parse this, find "WebSearchTool", and call its .use() method.
// The tool's output is then fed back into the LLM conversation.
```

### Human-in-the-Loop (HITL)

A task can be marked as requiring human input.

1.  **Define Task:**
    ```java
    // Task hitlTask = new Task("Review this draft", Map.of("draft", "..."), "Reviewed draft", true); // true for requiresHumanInput
    ```
2.  **Agent Execution:** When an agent like `BasicAgent` receives such a task and `humanInput` is not yet set, it will:
    *   Set the task's status to `AWAITING_HUMAN_INPUT`.
    *   Store a `CompletableFuture` handle in the task via `task.setExternalCompletionHandle()`.
    *   The `agent.performTask()` method returns a future that will only complete after this handle is externally completed.
3.  **Providing Input:** An external system or user interface would monitor tasks `AWAITING_HUMAN_INPUT`.
    ```java
    // if (hitlTask.getStatus() == TaskStatus.AWAITING_HUMAN_INPUT) {
    //     String humanFeedback = "This looks good, proceed."; // Get actual human input
    //     hitlTask.setHumanInput(humanFeedback); // This completes the internal future
    // }
    ```
    Once `setHumanInput()` is called, the agent's paused operation resumes, using the human's input as its result for that step.

### Memory Usage

Agents use their `Memory` (e.g., `ShortTermMemory`) to store and retrieve information.

1.  **Storing Information:**
    ```java
    // Inside BasicAgent, after a successful tool use or task completion:
    // agent.getMemory().add("tool_interaction:SearchTool:" + task.getId(), toolOutput);
    // agent.getMemory().add("task_summary:" + task.getId() + ":" + task.getDescription(), finalLLMAnswer);
    ```
2.  **Retrieving Information for Prompts:**
    *   `BasicAgent` automatically searches its memory based on the current task's description (e.g., `memory.search(task.getDescription(), 3)`).
    *   This retrieved information is then added to the prompt provided to the LLM, under a section like "Relevant Information from Memory:", helping the LLM make more informed decisions.

## 6. LLM Integration

The framework interacts with Large Language Models via the `LLMClient` interface (`com.javaagentai.aiagents.llm.LLMClient`). This interface defines methods like `complete(String prompt)` for getting responses from an LLM.

While specific implementations are not part of the core framework structure provided initially, the design allows for plugging in any LLM. Based on common Java libraries and potential `pom.xml` entries, planned or example integrations could include:

*   **Vertex AI Gemini/PaLM:** Using Google Cloud's Java SDK.
*   **OpenAI GPT Models:** Via libraries like `openai-java`.
*   **Anthropic Claude:** If a Java SDK becomes available or via direct HTTP.
*   **Groq API:** For fast inference, accessible via HTTP.

Developers would implement the `LLMClient` interface for their chosen LLM provider.

## 7. Future Work / Roadmap (Optional)

*   **True Long-Term Memory:** Integration with vector databases (e.g., Pinecone, Weaviate, Milvus, PGVector) for persistent and scalable memory.
*   **Advanced Tool Schemas:** Support for more complex tool definitions and function calling capabilities similar to OpenAI Functions.
*   **More Process Strategies:** Explore additional strategies like voting mechanisms or more dynamic agent selection.
*   **User Interface for HITL:** A simple UI to manage tasks awaiting human input.
*   **Enhanced Error Handling & Resilience:** More sophisticated error handling within processes.
*   **Inter-Agent Communication:** Standardized protocols for direct agent-to-agent messaging.
*   **Configuration Management:** Easier ways to configure agents, tools, and LLMs.

## 8. Contributing

Contributions are welcome! Please refer to `CONTRIBUTING.md` for guidelines on how to contribute to this project, including coding standards, testing, and pull request processes.

## 9. License

This project is licensed under the **Apache 2.0 License**. See the `LICENSE` file for details.
