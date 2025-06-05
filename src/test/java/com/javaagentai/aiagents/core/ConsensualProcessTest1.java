package com.javaagentai.aiagents.core;



import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class ConsensualProcessTest1 {

    private ConsensualProcess process;
    private AgentContext context;
    private Agent agent1;
    private Agent agent2;
    private Task task;
    private Consumer<TaskResult> callback;

    @BeforeEach
    void setUp() {
        process = new ConsensualProcess();
        context = mock(AgentContext.class);
        agent1 = mock(Agent.class);
        agent2 = mock(Agent.class);
        task = mock(Task.class);
        callback = mock(Consumer.class);
        // Create a Map for task input
        Map<String, Object> taskInput = new HashMap<>();
        taskInput.put("inputData", "Input Data");
        taskInput.put("additionalInfo", "Some Metadata");
        // Common setup for task
        when(task.getId()).thenReturn("task1");
        when(task.getDescription()).thenReturn("Test Task");
        when(task.getInput()).thenReturn(taskInput);
        when(task.getExpectedOutput()).thenReturn("Expected Output");
        when(task.getCallback()).thenReturn(callback);
    }

    @Test
    void testExecuteWithNoAgents() {
        // Arrange
        when(task.isRequiresHumanInput()).thenReturn(true);
        when(task.getHumanInput()).thenReturn("Human Input");

        // Act
        CompletableFuture<String> result = process.execute(task, null, context);

        // Assert
        assertEquals("Error: No agents available.", result.join());
        verify(task).setStatus(TaskStatus.FAILED);
        ArgumentCaptor<TaskResult> captor = ArgumentCaptor.forClass(TaskResult.class);
        verify(callback).accept(captor.capture());

        verify(context).log(contains("No agents available"));
    }

    @Test
    void testExecuteWithSingleAgentRequiringHumanInput() {
        // Arrange
        when(task.isRequiresHumanInput()).thenReturn(true);
        when(task.getHumanInput()).thenReturn("Human Input");
        when(agent1.getName()).thenReturn("Agent1");
        when(agent1.performTask(any(Task.class), eq(context)))
                .thenReturn(CompletableFuture.completedFuture("Agent1 Output"));

        // Act
        CompletableFuture<String> result = process.execute(task, List.of(agent1), context);

        // Assert
        assertEquals("Agent1 Output", result.join());
        ArgumentCaptor<Task> taskCaptor = ArgumentCaptor.forClass(Task.class);
        verify(agent1).performTask(taskCaptor.capture(), eq(context));
        Task capturedTask = taskCaptor.getValue();
        assertTrue(capturedTask.isRequiresHumanInput());
        assertEquals("Human Input", capturedTask.getHumanInput());
        verify(context).log(contains("Only one agent (Agent1) available"));
    }

    @Test
    void testExecuteWithSingleAgentMissingHumanInput() {
        // Arrange
        when(task.isRequiresHumanInput()).thenReturn(true);
        when(task.getHumanInput()).thenReturn(null);
        when(agent1.getName()).thenReturn("Agent1");
        when(agent1.performTask(any(Task.class), eq(context)))
                .thenReturn(CompletableFuture.completedFuture("Agent1 Output"));

        // Act
        CompletableFuture<String> result = process.execute(task, List.of(agent1), context);

        // Assert
        assertEquals("Agent1 Output", result.join());
        ArgumentCaptor<Task> taskCaptor = ArgumentCaptor.forClass(Task.class);
        verify(agent1).performTask(taskCaptor.capture(), eq(context));
        Task capturedTask = taskCaptor.getValue();
        assertTrue(capturedTask.isRequiresHumanInput());
        assertNull(capturedTask.getHumanInput());
        verify(context).log(contains("Only one agent (Agent1) available"));
    }

    @Test
    void testExecuteWithMultipleAgentsAndHumanInput() {
        // Arrange
        when(task.isRequiresHumanInput()).thenReturn(true);
        when(task.getHumanInput()).thenReturn("Human Input");
        when(agent1.getName()).thenReturn("Agent1");
        when(agent2.getName()).thenReturn("Agent2");
        when(agent1.performTask(any(Task.class), eq(context)))
                .thenReturn(CompletableFuture.completedFuture("Agent1 Output"));
        when(agent2.performTask(any(Task.class), eq(context)))
                .thenReturn(CompletableFuture.completedFuture("Agent2 Output"));
        BasicAgent basicAgent1 = mock(BasicAgent.class);
        when(basicAgent1.getMemory()).thenReturn(null);
        when(agent1).thenReturn(basicAgent1);

        // Act
        CompletableFuture<String> result = process.execute(task, Arrays.asList(agent1, agent2), context);

        // Assert
        assertEquals("Agent2 Output", result.join());
        ArgumentCaptor<Task> taskCaptor = ArgumentCaptor.forClass(Task.class);
        verify(agent1).performTask(taskCaptor.capture(), eq(context));
        verify(agent2, times(2)).performTask(taskCaptor.capture(), eq(context)); // Once for task, once for synthesis
        List<Task> capturedTasks = taskCaptor.getAllValues();
        assertEquals(3, capturedTasks.size());
        assertTrue(capturedTasks.get(0).isRequiresHumanInput());
        assertEquals("Human Input", capturedTasks.get(0).getHumanInput());
        assertTrue(capturedTasks.get(1).isRequiresHumanInput());
        assertEquals("Human Input", capturedTasks.get(1).getHumanInput());
        assertFalse(capturedTasks.get(2).isRequiresHumanInput()); // Synthesis task
        assertTrue(capturedTasks.get(2).getDescription().contains("Synthesize a final answer"));
        verify(context).log(contains("All agents completed parallel execution"));
        verify(context).log(contains("Asking synthesizer agent Agent2"));
    }

    @Test
    void testExecuteWithAgentFailure() {
        // Arrange
        when(task.isRequiresHumanInput()).thenReturn(true);
        when(task.getHumanInput()).thenReturn("Human Input");
        when(agent1.getName()).thenReturn("Agent1");
        when(agent2.getName()).thenReturn("Agent2");
        when(agent1.performTask(any(Task.class), eq(context)))
                .thenReturn(CompletableFuture.supplyAsync(() -> {
                    throw new RuntimeException("Agent1 Failed");
                }));
        when(agent2.performTask(any(Task.class), eq(context)))
                .thenReturn(CompletableFuture.completedFuture("Agent2 Output"));
        BasicAgent basicAgent1 = mock(BasicAgent.class);
        when(basicAgent1.getMemory()).thenReturn(null);
        when(agent1).thenReturn(basicAgent1);

        // Act
        CompletableFuture<String> result = process.execute(task, Arrays.asList(agent1, agent2), context);

        // Assert
        assertEquals("Agent2 Output", result.join());
        ArgumentCaptor<Task> taskCaptor = ArgumentCaptor.forClass(Task.class);
        verify(agent1).performTask(taskCaptor.capture(), eq(context));
        verify(agent2, times(2)).performTask(taskCaptor.capture(), eq(context));
        List<Task> capturedTasks = taskCaptor.getAllValues();
        assertTrue(capturedTasks.get(0).isRequiresHumanInput());
        assertEquals("Human Input", capturedTasks.get(0).getHumanInput());
        assertTrue(capturedTasks.get(1).isRequiresHumanInput());
        assertEquals("Human Input", capturedTasks.get(1).getHumanInput());
        assertTrue(capturedTasks.get(2).getDescription().contains("Agent1 said: 'Error: Agent1 Failed'"));
        verify(context).log(contains("Agent Agent1 failed for task task1"));
    }

    @Test
    void testExecuteWithSynthesisFailure() {
        // Arrange
        when(task.isRequiresHumanInput()).thenReturn(true);
        when(task.getHumanInput()).thenReturn("Human Input");
        when(agent1.getName()).thenReturn("Agent1");
        when(agent2.getName()).thenReturn("Agent2");
        when(agent1.performTask(any(Task.class), eq(context)))
                .thenReturn(CompletableFuture.completedFuture("Agent1 Output"));
        when(agent2.performTask(any(Task.class), eq(context)))
                .thenAnswer(invocation -> {
                    Task t = invocation.getArgument(0);
                    if (t.getDescription().contains("Synthesize")) {
                        throw new RuntimeException("Synthesis Failed");
                    }
                    return CompletableFuture.completedFuture("Agent2 Output");
                });
        BasicAgent basicAgent1 = mock(BasicAgent.class);
        when(basicAgent1.getMemory()).thenReturn(null);
        when(agent1).thenReturn(basicAgent1);

        // Act
        CompletableFuture<String> result = process.execute(task, Arrays.asList(agent1, agent2), context);

        // Assert
        assertTrue(result.join().startsWith("Error: Consensual process failed."));
        verify(task).setStatus(TaskStatus.FAILED);
        ArgumentCaptor<TaskResult> captor = ArgumentCaptor.forClass(TaskResult.class);
        verify(callback).accept(captor.capture());

        verify(context).log(contains("An error occurred during the consensual process"));
    }
}