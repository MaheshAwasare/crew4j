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
public class GroqClient implements LLMClient {

    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final ObjectMapper mapper;

    public GroqClient(String apiKey, String model) {
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
                    "messages", List.of(Map.of(
                            "role", "user",
                            "content", prompt
                    ))
            );

            String requestBody = mapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return "[Groq error: " + response.statusCode() + "]";
            }

            Map<?, ?> json = mapper.readValue(response.body(), Map.class);
            List<?> choices = (List<?>) json.get("choices");

            if (choices != null && !choices.isEmpty()) {
                Map<?, ?> choice = (Map<?, ?>) choices.get(0);
                Map<?, ?> message = (Map<?, ?>) choice.get("message");
                return (String) message.get("content");
            }

            return "[Groq error: unable to retrieve response]";

        } catch (Exception e) {
            return "[Groq error: " + e.getMessage() + "]";
        }
    }

    @Override
    public void close() {

    }
}