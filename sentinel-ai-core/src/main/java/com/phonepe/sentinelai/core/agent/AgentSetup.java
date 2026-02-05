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

package com.phonepe.sentinelai.core.agent;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.phonepe.sentinelai.core.events.EventBus;
import com.phonepe.sentinelai.core.model.Model;
import com.phonepe.sentinelai.core.model.ModelSettings;
import com.phonepe.sentinelai.core.model.OutputGenerationMode;

import lombok.Builder;
import lombok.Value;
import lombok.With;

import java.util.concurrent.ExecutorService;
import java.util.function.UnaryOperator;

/**
 * Details to setup an agent.
 * This _could_ be a part of the agent itself, but it's a bit cleaner to separate it out,
 * as it provides us a way to keep adding functionality to the agents without all sub classes needing to be updated.
 * Tools have been kept out of this so that same setup can be used with multiple agents each with their own tools.
 */
@Value
@Builder
@With
public class AgentSetup {
    /**
     * The object mapper to use for serialization/deserialization. If not provided, a default one will be created.
     */
    ObjectMapper mapper;
    /**
     * The LLM to be used for the agent. This can be provided at runtime as well. If neither are provided an error
     * will be thrown at runtime.
     */
    Model model;
    /**
     * The settings for the model. This can be provided at runtime as well. If neither are provided an error will be
     * thrown at runtime.
     */
    ModelSettings modelSettings;

    /**
     * The executor service to use for running the agent. If not provided, a default cached thread pool will be
     * created. This is used for LLM calls as well as tool execution.
     */
    ExecutorService executorService;


    /**
     * EventBus to be used for the agent. Ifn ot provided a default event bus is created.
     */
    EventBus eventBus;

    /**
     * Output generation mode to use for this model. Typically, other than OpenAI models, it is safer to leave it at
     * the default {@link OutputGenerationMode#TOOL_BASED}.
     */
    OutputGenerationMode outputGenerationMode;

    /**
     * Output generation tool to be used for tool based output
     */
    UnaryOperator<String> outputGenerationTool;

    /**
     * Retry setup for model calls
     */
    RetrySetup retrySetup;
}
