package com.phonepe.sentinelai.core.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class EnvLoaderTest {

    @Test
    void testReadEnvFromSystem() {
        // PATH is usually present in all environments
        final var path = EnvLoader.readEnv("PATH", null);
        assertNotNull(path, "PATH should be readable from system environment");
    }

    @Test
    void testReadEnvWithDefault() {
        final var variable = "NON_EXISTENT_VAR_" + System.currentTimeMillis();
        final var defaultValue = "default_value";
        
        final var result = EnvLoader.readEnv(variable, defaultValue);
        assertEquals(defaultValue, result);
    }

    @Test
    void testReadEnvOptional() {
        final var variable = "NON_EXISTENT_VAR_" + System.currentTimeMillis();
        Optional<String> result = EnvLoader.readEnv(variable);
        assertTrue(result.isEmpty());
    }

    @Test
    void testReadEnvWithNullDefault() {
        final var variable = "NON_EXISTENT_VAR_" + System.currentTimeMillis();
        final var result = EnvLoader.readEnv(variable, null);
        assertNull(result);
    }
    
    @Test
    void testReadEnvFromDotEnvFile() {
        // This test assumes the 'dotenv.file' system property is set to 'test.env'
        // which contains TEST_VAR=test_value_from_file
        final var result = EnvLoader.readEnv("TEST_VAR", null);
        
        // If the property is set correctly, this should not be null
        // We'll rely on Maven config to ensure this works in CI
        if (System.getProperty("dotenv.file") != null) {
            assertEquals("test_value_from_file", result);
        }
    }

    @Test
    void testReadEnvDotenvMock() {
        // Test the package-private method with a mock Dotenv for full branch coverage
        var mockDotenv = org.mockito.Mockito.mock(io.github.cdimascio.dotenv.Dotenv.class);
        
        // Case 1: value found in dotenv
        org.mockito.Mockito.when(mockDotenv.get("MOCK_VAR")).thenReturn("mock_value");
        assertEquals("mock_value", EnvLoader.readEnv(mockDotenv, "MOCK_VAR", "default"));
        
        // Case 2: value NOT found in dotenv, NOT found in system, use default
        org.mockito.Mockito.when(mockDotenv.get("NON_EXISTENT")).thenReturn(null);
        final var variable = "REALLY_NON_EXISTENT_" + System.currentTimeMillis();
        assertEquals("default", EnvLoader.readEnv(mockDotenv, variable, "default"));
    }
}
