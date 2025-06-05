package com.javaagentai.aiagents.config;

import org.junit.jupiter.api.Test;
import  org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
// For more advanced environment variable testing, one might use JUnit Pioneer:
// import org.junitpioneer.jupiter.ClearEnvironmentVariable;
// import org.junitpioneer.jupiter.SetEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.*;

public class ConfigTest {

    // Note: Config.java uses a static initializer, so it loads aiagents.properties from the classpath.
    // When tests are run, src/test/resources is typically on the classpath before src/main/resources,
    // so src/test/resources/aiagents.properties (created in previous step) will be used.

    @Test
    void testGetString_ValuePresent() {
        assertEquals("TestLLMProvider", Config.getString("llm.default.provider", "Default"),
                "Should retrieve value from test_aiagents.properties");
    }

    @Test
    void testGetString_ValueMissing_ReturnsDefault() {
        assertEquals("DefaultValueForMissingKey", Config.getString("key.that.does.not.exist", "DefaultValueForMissingKey"),
                "Should return default value for missing key.");
    }

    @Test
    void testGetInt_ValuePresentAndValid() {
        assertEquals(123, Config.getInt("test.int.value", 0),
                "Should retrieve and parse integer from test_aiagents.properties");
    }

    @Test
    void testGetInt_ValueInvalid_ReturnsDefault() {
        assertEquals(999, Config.getInt("test.int.invalid", 999),
                "Should return default for invalid integer format.");
    }

    @Test
    void testGetInt_ValueMissing_ReturnsDefault() {
        assertEquals(42, Config.getInt("test.int.that.is.missing", 42),
                "Should return default for missing integer key.");
    }

    // --- Test getEnvOrConfig ---
    // These tests are limited without a library like JUnit Pioneer to truly mock environment variables.
    // We test based on the properties file and *unset* environment variables primarily.

    @Test
    void testGetEnvOrConfig_ConfigValueOnly() {
        assertEquals("ActualConfigValue", Config.getEnvOrConfig("config.value.only", "UNUSED_ENV_VAR", "Default"),
                "Should use value directly from config file.");
    }
    
    @Test
    @DisabledIfEnvironmentVariable(named = "TEST_ENV_VAR_FOR_CONFIG", matches = ".*") // Ensure it's not set from outside
    void testGetEnvOrConfig_ConfigPointsToUnsetEnv_UsesDefault() {
         // Assuming TEST_ENV_VAR_FOR_CONFIG is NOT set in the test environment
        assertEquals("DefaultValueWhenEnvUnset", Config.getEnvOrConfig("config.value.points.to.env", "TEST_ENV_VAR_FOR_CONFIG", "DefaultValueWhenEnvUnset"),
                "Should use default if config points to an unset env var.");
    }

    @Test
    @DisabledIfEnvironmentVariable(named = "CONFIG_VALUE_IS_ENV_LITERAL_ENV", matches = ".*")
    void testGetEnvOrConfig_ConfigIsLiteralENV_UsesDefaultIfEnvUnset() {
        // config.value.is.env.literal=ENV
        // This tells getEnvOrConfig to use the provided envVarName (second param)
        assertEquals("Default", Config.getEnvOrConfig("config.value.is.env.literal", "CONFIG_VALUE_IS_ENV_LITERAL_ENV", "Default"),
            "Should use default if config is 'ENV' and the associated env var is unset.");
    }
    
    @Test
    @DisabledIfEnvironmentVariable(named = "DOLLAR_ENV_VAR_NAME", matches = ".*")
    void testGetEnvOrConfig_ConfigIsDollarPrefixedUnsetEnv_UsesDefault() {
        // config.value.dollar.env = $DOLLAR_ENV_VAR_NAME
        assertEquals("Default", Config.getEnvOrConfig("config.value.dollar.env", "UNUSED_ENV_VAR", "Default"),
            "Should use default if config is '$ENV_VAR' and $ENV_VAR is unset.");
    }
    
    @Test
    @DisabledIfEnvironmentVariable(named = "UNRESOLVED_DOLLAR_ENV_VAR_NAME", matches = ".*")
    @DisabledIfEnvironmentVariable(named = "FALLBACK_ENV_VAR_NAME", matches = ".*")
    void testGetEnvOrConfig_ConfigIsDollarPrefixedUnsetEnv_UsesEnvVarNameParamIfSet_ElseDefault() {
        // config.value.dollar.env.unresolved = $UNRESOLVED_DOLLAR_ENV_VAR_NAME
        // Here, $UNRESOLVED_DOLLAR_ENV_VAR_NAME is assumed to be unset.
        // The method should then try the envVarName parameter ("FALLBACK_ENV_VAR_NAME"), which is also assumed unset.
        assertEquals("DefaultValueForUnresolvedDollar", 
            Config.getEnvOrConfig("config.value.dollar.env.unresolved", "FALLBACK_ENV_VAR_NAME", "DefaultValueForUnresolvedDollar"),
            "Should use default if both $VAR and explicit envVarName param are unset.");
    }


    // --- Test Specific Getters ---

    @Test
    void testGetDefaultLlmProvider() {
        assertEquals("TestLLMProvider", Config.getDefaultLlmProvider());
    }

    @Test
    @DisabledIfEnvironmentVariable(named = "TEST_LLM_API_KEY_ENV_NAME", matches = ".*")
    void testGetLlmApiKey_EnvVarNameFromConfig_EnvUnset() {
        // llm.testllmprovider.api.key.env=TEST_LLM_API_KEY_ENV_NAME
        // Assuming TEST_LLM_API_KEY_ENV_NAME is NOT set
        assertNull(Config.getLlmApiKey("TestLLMProvider"), 
            "API key should be null if env var specified in config is not set.");
    }
    
    @Test
    @DisabledIfEnvironmentVariable(named = "CONVENTIONAL_API_KEY", matches = ".*")
    void testGetLlmApiKey_NoConfigForEnvVarName_ConventionalEnvUnset() {
        // Assuming "UnknownProvider" has no 'llm.unknownprovider.api.key.env' in properties
        // And UNKNOWNPROVIDER_API_KEY is not set
        assertNull(Config.getLlmApiKey("UnknownProvider"),
            "API key should be null if no config for env var name and conventional env var is not set.");
    }


    @Test
    void testGetLlmModel() {
        assertEquals("test-model-v1", Config.getLlmModel("TestLLMProvider"));
        assertEquals("another-model-x", Config.getLlmModel("AnotherLLM"));
        assertNull(Config.getLlmModel("UnknownProvider"), "Model should be null for unknown provider without default.");
    }

    @Test
    void testGetLlmGcpConfig() {
        assertEquals("test-gcp-project-id", Config.getLlmGcpProjectId());
        assertEquals("test-gcp-location", Config.getLlmGcpLocation());
        assertEquals("test-gemini-model", Config.getLlmModel("VertexAI")); // Checks specific model for VertexAI
    }

    @Test
    void testGetDefaultMemoryType() {
        assertEquals("TestMemoryType", Config.getDefaultMemoryType());
    }

    @Test
    void testGetFileBasedLtmConfig() {
        assertEquals("./test_ltm_data/test_vector_store.json", Config.getFileBasedLtmFilepath());
        assertEquals("TestFileEmbeddingClient", Config.getFileBasedLtmEmbeddingClientType());
        assertEquals(128, Config.getFileBasedLtmEmbeddingMockDimension());
    }

    @Test
    void testGetChromaDbLtmConfig() {
        assertEquals("http://testhost:8888", Config.getChromaDbLtmUrl());
        assertEquals("test_ai_agents_collection", Config.getChromaDbLtmCollectionName());
        assertEquals("TestChromaEmbeddingClient", Config.getChromaDbLtmEmbeddingClientType());
        assertEquals(256, Config.getChromaDbLtmEmbeddingMockDimension());
    }
    
    @Test
    void testGetMockEmbeddingDimension_General() {
        assertEquals(64, Config.getMockEmbeddingDimension());
    }
}
