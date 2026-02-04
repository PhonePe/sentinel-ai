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

package com.phonepe.sentinelai.core.model;

/**
 * Mode for generating output from an agent.
 */
public enum OutputGenerationMode {
    /**
     * Generate output using a tool. The output is passed as a parameter to the tool. This is the default mode for
     * output generation.
     */
    TOOL_BASED,

    /**
     * Generate output using a structured output format. The output is expected to be in a specific format. This is not
     * supported by many models and some models might not support tool call and structured output at the same time.
     */
    STRUCTURED_OUTPUT,
}
