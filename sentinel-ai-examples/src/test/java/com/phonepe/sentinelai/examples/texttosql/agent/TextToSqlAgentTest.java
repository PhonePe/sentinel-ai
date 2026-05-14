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

package com.phonepe.sentinelai.examples.texttosql.agent;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.model.Model;
import com.phonepe.sentinelai.core.model.OutputGenerationMode;
import com.phonepe.sentinelai.core.outputvalidation.OutputValidationResults;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class TextToSqlAgentTest {

    private TextToSqlAgent agent;

    @Test
    void agentIsNonNull() {
        assertNotNull(agent);
    }

    @Test
    void builderThrowsWhenSetupIsNull() {
        final var builder = TextToSqlAgent.builder();
        builder.outputValidator((ctx, out) -> OutputValidationResults.success());
        assertThrows(NullPointerException.class, () -> builder.setup(null));
    }

    @Test
    void builderWithNoExtensionsSucceeds() {
        final var model = mock(Model.class);
        final var setup = AgentSetup.builder()
                .mapper(new ObjectMapper())
                .model(model)
                .outputGenerationMode(OutputGenerationMode.TOOL_BASED)
                .outputGenerationTool(result -> result)
                .build();
        final var a = TextToSqlAgent.builder()
                .setup(setup)
                .outputValidator((ctx, out) -> OutputValidationResults.success())
                .build();
        assertNotNull(a);
        assertEquals("text-to-sql-agent", a.name());
    }

    @Test
    void builderWithNullOutputValidatorUsesDefault() {
        final var model = mock(Model.class);
        final var setup = AgentSetup.builder()
                .mapper(new ObjectMapper())
                .model(model)
                .outputGenerationMode(OutputGenerationMode.TOOL_BASED)
                .outputGenerationTool(result -> result)
                .build();
        final var builder = TextToSqlAgent.builder();
        builder.setup(setup);
        assertThrows(NullPointerException.class, () -> builder.outputValidator(null));
    }

    @Test
    void nameReturnsExpected() {
        assertEquals("text-to-sql-agent", agent.name());
    }

    @BeforeEach
    void setUp() {
        final var model = mock(Model.class);
        final var setup = AgentSetup.builder()
                .mapper(new ObjectMapper())
                .model(model)
                .outputGenerationMode(OutputGenerationMode.TOOL_BASED)
                .outputGenerationTool(result -> result)
                .build();
        agent = TextToSqlAgent.builder()
                .setup(setup)
                .outputValidator((context, output) -> OutputValidationResults.success())
                .build();
    }
}
