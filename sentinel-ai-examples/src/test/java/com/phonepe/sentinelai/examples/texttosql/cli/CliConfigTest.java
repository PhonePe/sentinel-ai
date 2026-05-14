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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliConfigTest {

    @Nested
    class AgentConfigDefaults {

        @Test
        void defaultMaxTokensIs4096() {
            CliConfig.AgentConfig cfg = new CliConfig.AgentConfig();
            assertEquals(4096, cfg.getMaxTokens());
        }

        @Test
        void defaultStreamingIsTrue() {
            CliConfig.AgentConfig cfg = new CliConfig.AgentConfig();
            assertTrue(cfg.isStreaming());
        }

        @Test
        void defaultTemperatureIsZero() {
            CliConfig.AgentConfig cfg = new CliConfig.AgentConfig();
            assertEquals(0.0f, cfg.getTemperature(), 1e-6f);
        }

        @Test
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
    class DatabaseConfigDefaults {

        @Test
        void defaultPath() {
            CliConfig.DatabaseConfig cfg = new CliConfig.DatabaseConfig();
            assertEquals("./ecommerce.db", cfg.getPath());
        }

        @Test
        void pathSetterWorks() {
            CliConfig.DatabaseConfig cfg = new CliConfig.DatabaseConfig();
            cfg.setPath("/tmp/test.db");
            assertEquals("/tmp/test.db", cfg.getPath());
        }
    }

    @Nested
    class OpenAIConfigDefaults {

        @Test
        void defaultApiKeyAndBaseUrlAreNull() {
            CliConfig.OpenAIConfig cfg = new CliConfig.OpenAIConfig();
            assertNull(cfg.getApiKey());
            assertNull(cfg.getBaseUrl());
        }

        @Test
        void defaultBearerPrefix() {
            CliConfig.OpenAIConfig cfg = new CliConfig.OpenAIConfig();
            assertEquals("Bearer ", cfg.getBearerPrefix());
        }

        @Test
        void defaultModelIsGpt4o() {
            CliConfig.OpenAIConfig cfg = new CliConfig.OpenAIConfig();
            assertEquals("gpt-4o", cfg.getModel());
        }

        @Test
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

    @Nested
    class TopLevelDefaultsTests {

        @Test
        void defaultConstructionProducesDefaults() {
            CliConfig config = new CliConfig();

            assertNotNull(config.getOpenai());
            assertNotNull(config.getDatabase());
            assertNotNull(config.getAgent());
        }

        @Test
        void topLevelSetters() {
            CliConfig config = new CliConfig();
            CliConfig.OpenAIConfig openai = new CliConfig.OpenAIConfig();
            openai.setApiKey("my-key");
            config.setOpenai(openai);

            assertEquals("my-key", config.getOpenai().getApiKey());
        }
    }
}
