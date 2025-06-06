package com.javaagentai.aiagents.llm;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

/**
 * Author: Mahesh Awasare
 */
public class ClaudeClient implements LLMClient {
    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private ObjectMapper mapper = new ObjectMapper();

    public ClaudeClient(String apiKey, String model) {
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = "https://api.groq.com/openai/v1/chat/completions";
        this.mapper = new ObjectMapper();
    }

    @Override
    public String complete(String prompt) {
        try {
            Map<String, Object> body = Map.of(
                    "model", model,
                    "max_tokens", 1024,
                    "temperature", 0.7,
                    "messages", List.of(Map.of(
                            "role", "user",
                            "content", prompt
                    ))
            );

            String requestBody = mapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl))
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            Map<?, ?> json = mapper.readValue(response.body(), Map.class);
            List<?> contentList = (List<?>) json.get("content");

            if (contentList != null && !contentList.isEmpty()) {
                Map<?, ?> contentBlock = (Map<?, ?>) contentList.get(0);
                return (String) contentBlock.get("text");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "[Claude error]";
    }

    @Override
    public void close() {

    }
}
