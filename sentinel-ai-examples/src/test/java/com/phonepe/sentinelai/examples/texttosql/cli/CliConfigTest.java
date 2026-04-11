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

package com.phonepe.sentinelai.examples.texttosql.cli;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("CliConfig")
class CliConfigTest {

    @Test
    @DisplayName("default construction produces expected defaults")
    void defaultConstructionProducesDefaults() {
        CliConfig config = new CliConfig();

        assertNotNull(config.getOpenai());
        assertNotNull(config.getDatabase());
        assertNotNull(config.getAgent());
    }

    @Nested
    @DisplayName("AgentConfig defaults")
    class AgentConfigDefaults {

        @Test
        @DisplayName("default temperature is 0.0")
        void defaultTemperatureIsZero() {
            CliConfig.AgentConfig cfg = new CliConfig.AgentConfig();
            assertEquals(0.0f, cfg.getTemperature(), 1e-6f);
        }

        @Test
        @DisplayName("default maxTokens is 4096")
        void defaultMaxTokensIs4096() {
            CliConfig.AgentConfig cfg = new CliConfig.AgentConfig();
            assertEquals(4096, cfg.getMaxTokens());
        }

        @Test
        @DisplayName("default streaming is true")
        void defaultStreamingIsTrue() {
            CliConfig.AgentConfig cfg = new CliConfig.AgentConfig();
            assertTrue(cfg.isStreaming());
        }

        @Test
        @DisplayName("setters and getters work")
        void settersWork() {
            CliConfig.AgentConfig cfg = new CliConfig.AgentConfig();
            cfg.setTemperature(0.7f);
            cfg.setMaxTokens(2048);
            cfg.setStreaming(false);

            assertEquals(0.7f, cfg.getTemperature(), 1e-6f);
            assertEquals(2048, cfg.getMaxTokens());
            assertFalse(cfg.isStreaming());
        }
    }

    @Nested
    @DisplayName("DatabaseConfig defaults")
    class DatabaseConfigDefaults {

        @Test
        @DisplayName("default path is ./ecommerce.db")
        void defaultPath() {
            CliConfig.DatabaseConfig cfg = new CliConfig.DatabaseConfig();
            assertEquals("./ecommerce.db", cfg.getPath());
        }

        @Test
        @DisplayName("path setter works")
        void pathSetterWorks() {
            CliConfig.DatabaseConfig cfg = new CliConfig.DatabaseConfig();
            cfg.setPath("/tmp/test.db");
            assertEquals("/tmp/test.db", cfg.getPath());
        }
    }

    @Nested
    @DisplayName("OpenAIConfig defaults")
    class OpenAIConfigDefaults {

        @Test
        @DisplayName("default model is gpt-4o")
        void defaultModelIsGpt4o() {
            CliConfig.OpenAIConfig cfg = new CliConfig.OpenAIConfig();
            assertEquals("gpt-4o", cfg.getModel());
        }

        @Test
        @DisplayName("default bearerPrefix is 'Bearer '")
        void defaultBearerPrefix() {
            CliConfig.OpenAIConfig cfg = new CliConfig.OpenAIConfig();
            assertEquals("Bearer ", cfg.getBearerPrefix());
        }

        @Test
        @DisplayName("default apiKey and baseUrl are null")
        void defaultApiKeyAndBaseUrlAreNull() {
            CliConfig.OpenAIConfig cfg = new CliConfig.OpenAIConfig();
            assertNull(cfg.getApiKey());
            assertNull(cfg.getBaseUrl());
        }

        @Test
        @DisplayName("setters work")
        void settersWork() {
            CliConfig.OpenAIConfig cfg = new CliConfig.OpenAIConfig();
            cfg.setApiKey("sk-test");
            cfg.setBaseUrl("https://api.openai.com/v1");
            cfg.setModel("gpt-3.5-turbo");
            cfg.setBearerPrefix("Token ");

            assertEquals("sk-test", cfg.getApiKey());
            assertEquals("https://api.openai.com/v1", cfg.getBaseUrl());
            assertEquals("gpt-3.5-turbo", cfg.getModel());
            assertEquals("Token ", cfg.getBearerPrefix());
        }
    }

    @Test
    @DisplayName("top-level setters wire sub-configs")
    void topLevelSetters() {
        CliConfig config = new CliConfig();
        CliConfig.OpenAIConfig openai = new CliConfig.OpenAIConfig();
        openai.setApiKey("my-key");
        config.setOpenai(openai);

        assertEquals("my-key", config.getOpenai().getApiKey());
    }
}
