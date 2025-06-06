package com.javaagentai.aiagents.tools;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Author: Mahesh Awasare
 */
public class ExampleEchoTool implements Tool {

    @Override
    public String getName() {
        return "EchoTool";
    }

    @Override
    public String getDescription() {
        return "A simple tool that echoes back the input string. Parameters: 'input' (String)";
    }

    @Override
    public CompletableFuture<String> use(Map<String, Object> params) {
        if (params.containsKey("input") && params.get("input") instanceof String) {
            return CompletableFuture.completedFuture((String) params.get("input"));
        } else {
            // Wrapping error message in a completed future as well
            return CompletableFuture.completedFuture("Error: Missing or invalid 'input' parameter. Please provide a String value for 'input'.");
        }
    }

    @Override
    public Map<String, String> getParameterSchema() {
        return null;
    }
}
