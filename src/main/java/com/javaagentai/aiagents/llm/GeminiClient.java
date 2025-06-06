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
public class GeminiClient implements LLMClient {
    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private ObjectMapper mapper = new ObjectMapper();

    public GeminiClient(String apiKey, String model) {
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = "https://api.groq.com/openai/v1/chat/completions";
        this.mapper = new ObjectMapper();
    }

    @Override
    public String complete(String prompt) {
        try {
            Map<String, Object> body = Map.of(
                    "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt))))
            );

            String requestBody = mapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            Map<?, ?> json = mapper.readValue(response.body(), Map.class);
            List<?> candidates = (List<?>) json.get("candidates");
            if (!candidates.isEmpty()) {
                Map<?, ?> candidate = (Map<?, ?>) candidates.get(0);
                Map<?, ?> content = (Map<?, ?>) candidate.get("content");
                List<?> parts = (List<?>) content.get("parts");
                return (String) ((Map<?, ?>) parts.get(0)).get("text");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "[Gemini error]";
    }

    @Override
    public void close() {

    }
}
