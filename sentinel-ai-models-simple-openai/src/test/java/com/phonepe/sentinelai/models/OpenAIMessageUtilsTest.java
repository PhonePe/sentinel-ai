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

package com.phonepe.sentinelai.models;

import io.github.sashirestela.openai.common.tool.ToolChoiceOption;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.phonepe.sentinelai.core.model.OutputGenerationMode;
import com.phonepe.sentinelai.models.utils.OpenAIMessageUtils;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests {@link OpenAIMessageUtils#resolveToolChoice(OutputGenerationMode, SimpleOpenAIModelOptions.ToolChoice)}
 */
class OpenAIMessageUtilsTest {

    static Stream<Arguments> resolveToolChoiceCases() {
        return Stream.of(
                         // TOOL_BASED mode
                         Arguments.of(OutputGenerationMode.TOOL_BASED,
                                      SimpleOpenAIModelOptions.ToolChoice.REQUIRED,
                                      ToolChoiceOption.REQUIRED),
                         Arguments.of(OutputGenerationMode.TOOL_BASED,
                                      SimpleOpenAIModelOptions.ToolChoice.AUTO,
                                      ToolChoiceOption.AUTO),
                         Arguments.of(OutputGenerationMode.TOOL_BASED,
                                      SimpleOpenAIModelOptions.ToolChoice.DEFAULT,
                                      ToolChoiceOption.REQUIRED),
                         // STRUCTURED_OUTPUT mode
                         Arguments.of(OutputGenerationMode.STRUCTURED_OUTPUT,
                                      SimpleOpenAIModelOptions.ToolChoice.REQUIRED,
                                      ToolChoiceOption.REQUIRED),
                         Arguments.of(OutputGenerationMode.STRUCTURED_OUTPUT,
                                      SimpleOpenAIModelOptions.ToolChoice.AUTO,
                                      ToolChoiceOption.AUTO),
                         Arguments.of(OutputGenerationMode.STRUCTURED_OUTPUT,
                                      SimpleOpenAIModelOptions.ToolChoice.DEFAULT,
                                      ToolChoiceOption.AUTO)
        );
    }

    @ParameterizedTest(name = "mode={0}, toolChoice={1} => {2}")
    @MethodSource("resolveToolChoiceCases")
    void testResolveToolChoice(OutputGenerationMode mode,
                               SimpleOpenAIModelOptions.ToolChoice toolChoice,
                               ToolChoiceOption expected) {
        assertEquals(expected, OpenAIMessageUtils.resolveToolChoice(mode, toolChoice));
    }
}
