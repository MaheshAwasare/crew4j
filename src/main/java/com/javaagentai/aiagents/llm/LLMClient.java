package com.javaagentai.aiagents.llm;


import java.util.Map;

public interface LLMClient {
    String complete(String prompt);



    void close();
}

