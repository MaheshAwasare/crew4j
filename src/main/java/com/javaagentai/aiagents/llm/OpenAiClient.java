package com.javaagentai.aiagents.llm;


import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Author: Mahesh Awasare
 */
public class OpenAiClient implements LLMClient {
    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private ObjectMapper mapper = new ObjectMapper();

    public OpenAiClient(String apiKey, String model) {
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = "https://api.groq.com/openai/v1/chat/completions";
        this.mapper = new ObjectMapper();
    }

    @Override
    public String complete(String prompt) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            body.put("messages", List.of(Map.of("role", "user", "content", prompt)));
            body.put("temperature", 0.7);

            String requestBody = mapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            Map<?, ?> json = mapper.readValue(response.body(), Map.class);
            List<?> choices = (List<?>) json.get("choices");
            if (!choices.isEmpty()) {
                Map<?, ?> message = (Map<?, ?>) ((Map<?, ?>) choices.get(0)).get("message");
                return (String) message.get("content");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "[OpenAI error]";
    }

    @Override
    public void close() {

    }
}
