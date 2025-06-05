package com.javaagentai.aiagents.config;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Config {

    private static final Logger LOGGER = Logger.getLogger(Config.class.getName());
    private static final Properties properties = new Properties();
    private static final String PROPERTIES_FILE = "aiagents.properties";

    static {
        try (InputStream input = Config.class.getClassLoader().getResourceAsStream(PROPERTIES_FILE)) {
            if (input == null) {
                LOGGER.log(Level.SEVERE, "Unable to find " + PROPERTIES_FILE + " in classpath. Default values will be used.");
            } else {
                properties.load(input);
                LOGGER.info(PROPERTIES_FILE + " loaded successfully.");
            }
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Error loading " + PROPERTIES_FILE + ". Default values will be used.", ex);
        }
    }

    public static String getString(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    public static int getInt(String key, int defaultValue) {
        String value = properties.getProperty(key);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                LOGGER.log(Level.WARNING, "Failed to parse int for key '" + key + "'. Value: '" + value + "'. Using default: " + defaultValue, e);
            }
        }
        return defaultValue;
    }

    public static String getEnvOrConfig(String configKey, String envVarName, String defaultValue) {
        // Try config first
        String value = getString(configKey, null);

        if (value != null && !value.isEmpty()) {
            // If config value explicitly says to use ENV or if it's a placeholder for env var name
            if ("ENV".equalsIgnoreCase(value) || value.startsWith("$")) { // e.g. $ENV_VAR_NAME
                String actualEnvVarName = value.startsWith("$") ? value.substring(1) : envVarName;
                String envValue = System.getenv(actualEnvVarName);
                if (envValue != null && !envValue.isEmpty()) {
                    return envValue;
                }
                // If ENV was specified but env var is not set, log and fall through to default or specified envVarName
                LOGGER.warning("Config key '" + configKey + "' indicated ENV, but environment variable '" + actualEnvVarName + "' is not set or empty.");
            } else {
                return value; // Use the direct config value
            }
        }
        
        // If config value was null, empty, or pointed to an unset ENV var, try the specified envVarName
        if (envVarName != null && !envVarName.isEmpty()) {
            String envValue = System.getenv(envVarName);
            if (envValue != null && !envValue.isEmpty()) {
                return envValue;
            }
        }
        
        return defaultValue;
    }

    // Specific Getters

    // LLM Configuration
    public static String getDefaultLlmProvider() {
        return getString("llm.default.provider", "OpenAI");
    }

    public static String getLlmApiKey(String provider) {
        if (provider == null || provider.trim().isEmpty()) return null;
        String keySuffix = provider.toLowerCase() + ".api.key.env"; // e.g., openai.api.key.env
        String configKey = "llm." + keySuffix; // e.g., llm.openai.api.key.env
        String envVarNameFromConfig = getString(configKey, null); // e.g., OPENAI_API_KEY
        
        // If the config property itself holds the name of the environment variable
        if (envVarNameFromConfig != null && !envVarNameFromConfig.isEmpty()) {
            return getEnvOrConfig(configKey, envVarNameFromConfig, null); // Pass configKey to allow "ENV" override behavior
        }
        // Fallback to a conventional env var name if not specified in config
        String conventionalEnvVarName = provider.toUpperCase() + "_API_KEY";
        return getEnvOrConfig(configKey, conventionalEnvVarName, null);
    }

    public static String getLlmModel(String provider) {
        if (provider == null || provider.trim().isEmpty()) return null;
        return getString("llm." + provider.toLowerCase() + ".model", null);
    }

    public static String getLlmGcpProjectId() {
        return getString("llm.vertexai.project.id", null);
    }

    public static String getLlmGcpLocation() {
        return getString("llm.vertexai.location", "us-central1");
    }

    // Memory Configuration
    public static String getDefaultMemoryType() {
        return getString("memory.default.type", "ShortTermMemory");
    }

    public static String getFileBasedLtmFilepath() {
        return getString("memory.filebasedltm.filepath", "./ltm_data/vector_store.json");
    }

    public static String getFileBasedLtmEmbeddingClientType() {
        return getString("memory.filebasedltm.embedding.client", "MockEmbeddingClient");
    }

    public static int getFileBasedLtmEmbeddingMockDimension() {
        return getInt("memory.filebasedltm.embedding.mock.dimension", getMockEmbeddingDimension()); // Fallback to general mock dimension
    }

    public static String getChromaDbLtmUrl() {
        return getString("memory.chromadbltm.url", "http://localhost:8000");
    }

    public static String getChromaDbLtmCollectionName() {
        return getString("memory.chromadbltm.collection.name", "ai_agents_default");
    }

    public static String getChromaDbLtmEmbeddingClientType() {
        return getString("memory.chromadbltm.embedding.client", "MockEmbeddingClient");
    }

    public static int getChromaDbLtmEmbeddingMockDimension() {
        return getInt("memory.chromadbltm.embedding.mock.dimension", getMockEmbeddingDimension()); // Fallback to general mock dimension
    }
    
    // Embedding Configuration
    public static int getMockEmbeddingDimension() {
        return getInt("embedding.mock.dimension", 4); // General default
    }

    // Private constructor to prevent instantiation
    private Config() {}
}
