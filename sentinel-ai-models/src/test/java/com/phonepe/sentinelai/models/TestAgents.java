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

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.agent.AgentInput;
import com.phonepe.sentinelai.core.agent.AgentOutput;
import com.phonepe.sentinelai.core.agent.AgentRequestMetadata;
import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.agent.StreamConsumer;
import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.tools.Tool;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shared agents and helpers for the {@code ConfiguredModel} WireMock suites. The simple names of
 * the nested agent classes are load-bearing: tool ids are derived from the agent class simple
 * name ({@code TestAgent} -> {@code test_agent_*}, {@code OutputObjectAgent} ->
 * {@code output_object_agent_*}), so the wire fixtures keep resolving as long as those simple
 * names stay unchanged.
 */
@Slf4j
public final class TestAgents {

    /**
     * Structured-output agent for the fixtures that end in a structured output; its
     * {@code output_object_agent_*} tool id is asserted on the request side.
     */
    public static final class OutputObjectAgent extends Agent<OutputObject, OutputObject, OutputObjectAgent> {

        public OutputObjectAgent(@NonNull AgentSetup setup) {
            super(OutputObject.class,
                  "Greet the user by name and respond to queries",
                  setup,
                  List.of(),
                  Map.of());
        }

        @Tool("Get name of the user")
        public String getName() {
            return "Santanu";
        }

        @Override
        public String name() {
            return "test-agent";
        }
    }

    /**
     * String-typed agent for the tool-loop and streaming fixtures that speak
     * {@code test_agent_*} tool ids.
     */
    public static final class TestAgent extends Agent<String, String, TestAgent> {

        public final AtomicInteger getNameCalls = new AtomicInteger();

        public TestAgent(@NonNull AgentSetup setup) {
            super(String.class,
                  "Greet the user by name and respond to queries",
                  setup,
                  List.of(),
                  Map.of());
        }

        @Tool("Get location of the user")
        public String getLocation(@JsonPropertyDescription("User name") String name) {
            if (name.equalsIgnoreCase("santanu")) {
                return "Bangalore";
            }
            throw new IllegalArgumentException("Invalid parameter");
        }

        @Tool("Get name of the user")
        public String getName() {
            getNameCalls.incrementAndGet();
            return "Santanu";
        }

        @Tool("Get weather for city")
        public String getWeather(@JsonPropertyDescription("City name") String city) {
            if (city.equalsIgnoreCase("bangalore")) {
                return "Sunny";
            }
            throw new IllegalArgumentException("Invalid parameter");
        }

        @Override
        public String name() {
            return "test-agent";
        }
    }

    private TestAgents() {
    }

    /**
     * Counts the messages of the given type.
     */
    public static long countMessages(final List<AgentMessage> messages,
                                     final Class<? extends AgentMessage> type) {
        return messages.stream().filter(type::isInstance).count();
    }

    /**
     * Runs the agent with the standard greeting request.
     */
    public static AgentOutput<OutputObject> execute(final OutputObjectAgent agent) {
        return agent.execute(AgentInput.<OutputObject>builder()
                .request(new OutputObject(null, "Hi"))
                .requestMetadata(AgentRequestMetadata.builder().sessionId("s1").userId("ss").build())
                .build());
    }

    /**
     * A stream consumer that logs the received content.
     */
    public static StreamConsumer streamConsumer() {
        return new StreamConsumer() {
            @Override
            public void consumeContent(final String content) {
                log.info("RECEIVED: {}", content);
            }
        };
    }

    /**
     * The output object produced by {@link OutputObjectAgent}.
     */
    public record OutputObject(
            String username,
            String message
    ) {
    }
}
