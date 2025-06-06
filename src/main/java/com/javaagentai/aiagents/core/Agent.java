package com.javaagentai.aiagents.core;

import com.javaagentai.aiagents.memory.Memory;
import com.javaagentai.aiagents.tools.Tool;

import java.util.List;
import java.util.concurrent.CompletableFuture;


/**
 * Author: Mahesh Awasare
 */
public interface Agent {
    String getName();

    String getRole(); // Role is String as per BasicAgent

    List<Tool> getTools(); // Method from BasicAgent

    Memory getMemory(); // New method

    CompletableFuture<String> performTask(Task task, AgentContext context);
}
