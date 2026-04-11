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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.model.Model;
import com.phonepe.sentinelai.core.model.OutputGenerationMode;
import com.phonepe.sentinelai.core.outputvalidation.OutputValidationResults;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TextToSqlAgent")
class TextToSqlAgentTest {

    private TextToSqlAgent agent;

    @BeforeEach
    void setUp() {
        final Model model = mock(Model.class);
        final AgentSetup setup =
                AgentSetup.builder()
                        .mapper(new ObjectMapper())
                        .model(model)
                        .outputGenerationMode(OutputGenerationMode.TOOL_BASED)
                        .outputGenerationTool(result -> result)
                        .build();
        agent =
                TextToSqlAgent.builder()
                        .setup(setup)
                        .outputValidator((context, output) -> OutputValidationResults.success())
                        .build();
    }

    @Test
    @DisplayName("name() returns 'text-to-sql-agent'")
    void nameReturnsExpected() {
        assertEquals("text-to-sql-agent", agent.name());
    }

    @Test
    @DisplayName("agent is non-null after construction")
    void agentIsNonNull() {
        assertNotNull(agent);
    }

    @Test
    @DisplayName("builder with no extensions builds successfully")
    void builderWithNoExtensionsSucceeds() {
        final Model model = mock(Model.class);
        final AgentSetup setup =
                AgentSetup.builder()
                        .mapper(new ObjectMapper())
                        .model(model)
                        .outputGenerationMode(OutputGenerationMode.TOOL_BASED)
                        .outputGenerationTool(result -> result)
                        .build();
        final TextToSqlAgent a =
                TextToSqlAgent.builder()
                        .setup(setup)
                        .outputValidator((ctx, out) -> OutputValidationResults.success())
                        .build();
        assertNotNull(a);
        assertEquals("text-to-sql-agent", a.name());
    }

    @Test
    @DisplayName("builder throws NullPointerException when setup is null")
    void builderThrowsWhenSetupIsNull() {
        assertThrows(
                NullPointerException.class,
                () -> TextToSqlAgent.builder()
                        .setup(null)
                        .outputValidator((ctx, out) -> OutputValidationResults.success())
                        .build());
    }

    @Test
    @DisplayName("builder throws NullPointerException when outputValidator is null — uses default")
    void builderWithNullOutputValidatorUsesDefault() {
        final Model model = mock(Model.class);
        final AgentSetup setup =
                AgentSetup.builder()
                        .mapper(new ObjectMapper())
                        .model(model)
                        .outputGenerationMode(OutputGenerationMode.TOOL_BASED)
                        .outputGenerationTool(result -> result)
                        .build();
        // outputValidator is @NonNull in the builder — passing null should throw
        assertThrows(
                NullPointerException.class,
                () -> TextToSqlAgent.builder()
                        .setup(setup)
                        .outputValidator(null)
                        .build());
    }
}
