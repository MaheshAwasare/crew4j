package com.javaagentai.aiagents.llm;


public interface LLMClient {
    String complete(String prompt);


    void close();
}

