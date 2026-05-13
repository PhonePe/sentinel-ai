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

package com.phonepe.sentinelai.examples.texttosql.cli.support;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.agent.ModelOutputDefinition;
import com.phonepe.sentinelai.core.agent.ToolRunner;
import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.agentmessages.requests.UserPrompt;
import com.phonepe.sentinelai.core.earlytermination.EarlyTerminationStrategy;
import com.phonepe.sentinelai.core.hooks.AgentMessagesPreProcessor;
import com.phonepe.sentinelai.core.model.Model;
import com.phonepe.sentinelai.core.model.ModelOutput;
import com.phonepe.sentinelai.core.model.ModelRunContext;
import com.phonepe.sentinelai.core.model.ModelUsageStats;
import com.phonepe.sentinelai.core.tools.ExecutableTool;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class StubTextToSqlModel implements Model {
    @Override
    public CompletableFuture<ModelOutput> compute(ModelRunContext context,
                                                  Collection<ModelOutputDefinition> outputDefinitions,
                                                  List<AgentMessage> oldMessages,
                                                  Map<String, ExecutableTool> tools,
                                                  ToolRunner toolRunner,
                                                  EarlyTerminationStrategy earlyTerminationStrategy,
                                                  List<AgentMessagesPreProcessor> agentMessagesPreProcessors) {
        return CompletableFuture.supplyAsync(() -> {
            final var request = oldMessages.stream()
                    .filter(UserPrompt.class::isInstance)
                    .map(UserPrompt.class::cast)
                    .reduce((first, second) -> second)
                    .map(UserPrompt::getContent)
                    .orElse("unknown");

            final var outputNode = JsonNodeFactory.instance.objectNode();
            outputNode.put("generatedSql", "SELECT 1");
            outputNode.putArray("results").add("{\"answer\":1}");
            outputNode.put("explanation", "Generated for input: " + request);
            outputNode.put("executionTimeMs", 5L);

            final var data = JsonNodeFactory.instance.objectNode();
            data.set(Agent.OUTPUT_VARIABLE_NAME, outputNode);

            final var usage = new ModelUsageStats();
            usage.incrementRequestTokens(10)
                    .incrementResponseTokens(5)
                    .incrementTotalTokens(15)
                    .incrementRequestsForRun(1);

            final var allMessages = new ArrayList<>(oldMessages);
            return ModelOutput.success(data, List.of(), allMessages, usage);
        });
    }
}
