package com.javaagentai.aiagents.tools;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Author: Mahesh Awasare
 * Interface for defining tools that can be used by agents.
 * Tools are used to perform specific tasks or operations within the context of an agent.
 * Each tool should implement this interface to provide a consistent way of interacting with the agent system.
 */
public interface Tool {
    String getName();

    String getDescription();

    CompletableFuture<String> use(Map<String, Object> params);

    Map<String, String> getParameterSchema();
}
