/*
 * Copyright (c) 2025 Original Author(s), PhonePe India Pvt. Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.phonepe.sentinelai.core.utils;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnvLoaderTest {

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
    void testReadEnvFromSystem() {
        // PATH is usually present in all environments
        final var path = EnvLoader.readEnv("PATH", null);
        assertNotNull(path, "PATH should be readable from system environment");
    }

    @Test
    void testReadEnvOptional() {
        final var variable = "NON_EXISTENT_VAR_" + System.currentTimeMillis();
        Optional<String> result = EnvLoader.readEnv(variable);
        assertTrue(result.isEmpty());
    }

    @Test
    void testReadEnvWithDefault() {
        final var variable = "NON_EXISTENT_VAR_" + System.currentTimeMillis();
        final var defaultValue = "default_value";

        final var result = EnvLoader.readEnv(variable, defaultValue);
        assertEquals(defaultValue, result);
    }

    @Test
    void testReadEnvWithNullDefault() {
        final var variable = "NON_EXISTENT_VAR_" + System.currentTimeMillis();
        final var result = EnvLoader.readEnv(variable, null);
        assertNull(result);
    }
}
