package com.javaagentai.aiagents.tools;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface Tool {
    String getName();
    String getDescription();
    CompletableFuture<String> use(Map<String, Object> params);

    Map<String, String> getParameterSchema();
}
