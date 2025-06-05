package com.javaagentai.aiagents.llm;

import com.javaagentai.aiagents.config.Config; // Import the new Config class

import java.util.Map;

public class LLMClientFactory {

    // Default constructor, no specific config field needed at factory level anymore.

    /**
     * Creates an LLMClient instance based on the specified provider.
     * Configuration details (API key, model, etc.) are fetched from Config.java.
     *
     * @param provider The name of the LLM provider (e.g., "OpenAI", "VertexAI").
     * @return An instance of LLMClient.
     * @throws IllegalArgumentException if the provider is unsupported or required configuration is missing.
     */
    public static LLMClient createClient(String provider) {
        if (provider == null || provider.trim().isEmpty()) {
            throw new IllegalArgumentException("Provider name cannot be null or empty.");
        }

        String apiKey = Config.getLlmApiKey(provider); // Fetches based on "llm.<provider>.api.key.env"
        String modelName = Config.getLlmModel(provider);

        // Provider-specific checks or additional config
        // Note: Actual client implementations (OpenAiClient, GeminiClient/VertexAiClient)
        // will need to be adjusted or designed to accept these parameters.
        // For now, we are just showing how the factory gathers them.

        switch (provider.toLowerCase()) {
            case "openai":
                if (apiKey == null || apiKey.trim().isEmpty()) {
                    throw new IllegalArgumentException("OpenAI API key is missing. Set via " + Config.getString("llm.openai.api.key.env", "OPENAI_API_KEY") + " environment variable.");
                }
                if (modelName == null || modelName.trim().isEmpty()) {
                    throw new IllegalArgumentException("OpenAI model name is missing. Set llm.openai.model in properties.");
                }
                // return new OpenAiClient(apiKey, modelName); // Assuming OpenAiClient constructor
                System.out.println("LLMClientFactory: Would create OpenAiClient with model: " + modelName);
                // Placeholder: Replace with actual client instantiation when OpenAiClient is updated/created
                return new MockLLMClientImpl(provider, modelName, apiKey);


            case "vertexai": // Assuming "VertexAI" is the provider key in config for Gemini
                String gcpProjectId = Config.getLlmGcpProjectId();
                String gcpLocation = Config.getLlmGcpLocation();
                if (gcpProjectId == null || gcpProjectId.trim().isEmpty()) {
                    throw new IllegalArgumentException("VertexAI Project ID is missing. Set llm.vertexai.project.id in properties.");
                }
                if (modelName == null || modelName.trim().isEmpty()) {
                    throw new IllegalArgumentException("VertexAI (Gemini) model name is missing. Set llm.vertexai.model in properties.");
                }
                // API key for VertexAI is often handled by Application Default Credentials (ADC)
                // or service account keys, so direct apiKey might not always be used in constructor.
                // return new VertexAiClient(gcpProjectId, gcpLocation, modelName); // Assuming VertexAiClient (for Gemini)
                System.out.println("LLMClientFactory: Would create VertexAiClient (Gemini) with project: " + gcpProjectId + ", location: " + gcpLocation + ", model: " + modelName);
                // Placeholder
                return new MockLLMClientImpl(provider, modelName, "ADC_OR_SERVICE_ACCOUNT");


            // case "gemini": // If "gemini" is treated as a separate provider key
            //     // Similar logic to VertexAI or a dedicated Gemini client
            //     if (apiKey == null || apiKey.trim().isEmpty()) { // Gemini might use specific API keys too
            //         throw new IllegalArgumentException("Gemini API key is missing.");
            //     }
            //     // return new GeminiClient(apiKey, modelName);
            //     System.out.println("LLMClientFactory: Would create GeminiClient with model: " + modelName);
            //     return new MockLLMClientImpl(provider, modelName, apiKey);

            // case "claude":
            //     if (apiKey == null || apiKey.trim().isEmpty()) {
            //         throw new IllegalArgumentException("Claude API key is missing.");
            //     }
            //     // return new ClaudeClient(apiKey, modelName);
            //     System.out.println("LLMClientFactory: Would create ClaudeClient with model: " + modelName);
            //     return new MockLLMClientImpl(provider, modelName, apiKey);
            
            default:
                throw new IllegalArgumentException("Unsupported LLM provider: " + provider);
        }
    }

    /**
     * Creates an LLMClient instance for the default provider specified in Config.java.
     *
     * @return An instance of LLMClient for the default provider.
     * @throws IllegalArgumentException if the default provider is unsupported or required configuration is missing.
     */
    public static LLMClient createDefaultClient() {
        String defaultProvider = Config.getDefaultLlmProvider();
        if (defaultProvider == null || defaultProvider.trim().isEmpty()) {
            throw new IllegalStateException("Default LLM provider is not configured in aiagents.properties (llm.default.provider).");
        }
        System.out.println("LLMClientFactory: Creating client for default provider: " + defaultProvider);
        return createClient(defaultProvider);
    }

    // Simple Mock LLM Client implementation for placeholder purposes
    // This should ideally be in its own file or a test utility class.
    static class MockLLMClientImpl implements LLMClient {
        private final String provider;
        private final String model;
        private final String apiKeyDetails;

        public MockLLMClientImpl(String provider, String model, String apiKeyDetails) {
            this.provider = provider;
            this.model = model;
            this.apiKeyDetails = apiKeyDetails; // Could be API key or auth method
            System.out.println("MockLLMClientImpl created for " + provider + " model " + model + ". API Key/Auth: " + (apiKeyDetails != null && !apiKeyDetails.isEmpty() ? "Provided" : "Not Provided/Needed"));
        }

        @Override
        public String complete(String prompt) {
            System.out.println("MockLLMClientImpl (" + provider + "): Received prompt: " + prompt.substring(0, Math.min(50, prompt.length())) + "...");
            return "Mock response from " + provider + " for model " + model + ": " + prompt;
        }


        public String complete(String prompt, Map<String, Object> options) {
            return complete(prompt);
        }

        @Override
        public void close() {
            System.out.println("MockLLMClientImpl (" + provider + "): close() called.");
        }
    }
}
