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

package com.phonepe.sentinelai.core.compaction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.commons.text.StringSubstitutor;

import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.agent.ModelOutputDefinition;
import com.phonepe.sentinelai.core.agent.ProcessingMode;
import com.phonepe.sentinelai.core.agentmessages.AgentGenericMessage;
import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.agentmessages.AgentMessageVisitor;
import com.phonepe.sentinelai.core.agentmessages.AgentRequest;
import com.phonepe.sentinelai.core.agentmessages.AgentRequestVisitor;
import com.phonepe.sentinelai.core.agentmessages.AgentResponse;
import com.phonepe.sentinelai.core.agentmessages.AgentResponseVisitor;
import com.phonepe.sentinelai.core.agentmessages.requests.SystemPrompt;
import com.phonepe.sentinelai.core.agentmessages.requests.ToolCallResponse;
import com.phonepe.sentinelai.core.agentmessages.requests.UserPrompt;
import com.phonepe.sentinelai.core.agentmessages.responses.StructuredOutput;
import com.phonepe.sentinelai.core.agentmessages.responses.Text;
import com.phonepe.sentinelai.core.agentmessages.responses.ToolCall;
import com.phonepe.sentinelai.core.earlytermination.NeverTerminateEarlyStrategy;
import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.model.ModelRunContext;
import com.phonepe.sentinelai.core.model.ModelUsageStats;
import com.phonepe.sentinelai.core.tools.NonContextualDefaultExternalToolRunner;
import com.phonepe.sentinelai.core.utils.AgentUtils;
import com.phonepe.sentinelai.core.utils.JsonUtils;

import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.phonepe.sentinelai.core.utils.AgentUtils.sessionId;

@Slf4j
@UtilityClass
public class MessageCompactor {

    public static final String COMPACTION_SESSION_PREFIX = "compaction-for-";
    private static final String OUTPUT_KEY = "sessionOutput";

    @SneakyThrows
    public static CompletableFuture<Optional<ExtractedSummary>> compactMessages(final String agentName,
                                                                                final String sessionId,
                                                                                final String userId,
                                                                                final AgentSetup agentSetup,
                                                                                final ObjectMapper mapper,
                                                                                final ModelUsageStats stats,
                                                                                final List<AgentMessage> messages,
                                                                                final CompactionPrompts prompts,
                                                                                final int tokenBudget) {
        final var compactMessages = toCompactMessage(messages, mapper);
        final var messagesForCompaction = new ArrayList<AgentMessage>();
        final var runId = "run-for-" + COMPACTION_SESSION_PREFIX + Objects.requireNonNullElseGet(
                                                                                                 sessionId,
                                                                                                 () -> UUID
                                                                                                         .randomUUID()
                                                                                                         .toString());
        final var valueMap = Map.<String, Object>of("tokenBudget",
                                                    tokenBudget,
                                                    "sessionMessages",
                                                    mapper.writeValueAsString(compactMessages));
        final var systemPrompt = StringSubstitutor.replace(prompts
                .getSummarizationSystemPrompt(), valueMap);
        log.debug("Using summarization system prompt: {}", systemPrompt);
        messagesForCompaction.add(SystemPrompt.builder()
                .sessionId(sessionId)
                .runId(runId)
                .content(systemPrompt)
                .timestamp(AgentUtils.epochMicro())
                .build());
        String replace;
        try {
            replace = StringSubstitutor.replace(prompts
                    .getSummarizationUserPrompt(), valueMap);
        }
        catch (Exception e) {
            log.error("Error substituting values into user prompt template: %s"
                    .formatted(e.getMessage()), e);
            replace = mapper.writeValueAsString(valueMap);
        }
        final var userPrompt = replace;
        log.debug("Using summarization user prompt: {}", userPrompt);
        messagesForCompaction.add(UserPrompt.builder()
                .sessionId(sessionId)
                .runId(runId)
                .content(userPrompt)
                .timestamp(AgentUtils.epochMicro())
                .build());
        final var usageStats = Objects.requireNonNullElseGet(stats,
                                                             ModelUsageStats::new);
        final var modelRunContext = new ModelRunContext(agentName,
                                                        runId,
                                                        COMPACTION_SESSION_PREFIX + sessionId,
                                                        userId,
                                                        agentSetup
                                                                .withModelSettings(agentSetup
                                                                        .getModelSettings()
                                                                        .withParallelToolCalls(false)),
                                                        usageStats,
                                                        ProcessingMode.DIRECT);
        return agentSetup.getModel()
                .compute(modelRunContext,
                         List.of(ModelOutputDefinition.builder()
                                 .name(OUTPUT_KEY)
                                 .description("Output of the summarization run, containing the extracted summary and other relevant information as defined in the schema.")
                                 .schema(mapper.readTree(prompts.getPromptSchema()))
                                 .build()),
                         messagesForCompaction,
                         Map.of(),
                         new NonContextualDefaultExternalToolRunner(sessionId,
                                                                    runId,
                                                                    mapper),
                         new NeverTerminateEarlyStrategy(),
                         List.of())
                .thenApply(output -> {
                    log.debug("Summarization usage: {}", usageStats);
                    if (output.getError() != null && !output.getError()
                            .getErrorType()
                            .equals(ErrorType.SUCCESS)) {
                        log.error("Error extracting session summary: {}",
                                  output.getError());
                    }
                    else {
                        final var summaryData = output.getData().get(OUTPUT_KEY);
                        if (JsonUtils.empty(summaryData)) {
                            log.debug("No summary extracted from the output");
                        }
                        else {
                            log.debug("Extracted session summary output from summarization run: {}",
                                      summaryData);
                            try {
                                final var summary = ExtractedSummary.builder()
                                        .title(summaryData.get(ExtractedSummary.Fields.title).asText())
                                        .summary(summaryData.get(ExtractedSummary.Fields.summary).asText())
                                        .keywords(mapper.treeToValue(summaryData
                                                .get(ExtractedSummary.Fields.keywords),
                                                                     new TypeReference<List<String>>() {
                                                                     }))
                                        .rawData(summaryData)
                                        .build();

                                return Optional.of(summary);
                            }
                            catch (Exception e) {
                                log.error("Error extracting summary: %s"
                                        .formatted(e.getMessage()), e);
                            }
                        }
                    }
                    return Optional.<ExtractedSummary>empty();
                });
    }

    /**
     * Converts a list of AgentMessages to a JsonNode representing a list of CompactMessages without actually creating
     * CompactMessage Objects.
     *
     * @param messages List of AgentMessages to convert
     * @return JsonNode representing the list of CompactMessages
     */
    public static JsonNode toCompactMessage(List<AgentMessage> messages,
                                            final ObjectMapper mapper) {
        final var response = mapper.createArrayNode();
        for (AgentMessage message : messages) {
            final var convertedNode = message.accept(
                                                     new AgentMessageVisitor<JsonNode>() {
                                                         @Override
                                                         public JsonNode visit(AgentGenericMessage genericMessage) {
                                                             throw new UnsupportedOperationException("Unimplemented method 'visit'");
                                                         }

                                                         @Override
                                                         public JsonNode visit(AgentRequest request) {
                                                             return request
                                                                     .accept(new AgentRequestVisitor<>() {

                                                                         @Override
                                                                         public JsonNode visit(SystemPrompt systemPrompt) {
                                                                             final var node = mapper
                                                                                     .createObjectNode();
                                                                             node.put("type",
                                                                                      CompactMessage.Types.CHAT);
                                                                             node.put("role",
                                                                                      CompactMessage.Roles.SYSTEM);
                                                                             node.put("content",
                                                                                      systemPrompt.getContent());
                                                                             return node;
                                                                         }

                                                                         @Override
                                                                         public JsonNode visit(ToolCallResponse toolCallResponse) {
                                                                             final var node = mapper
                                                                                     .createObjectNode();
                                                                             node.put("type",
                                                                                      CompactMessage.Types.TOOL_CALL_RESPONSE);
                                                                             node.put("callId",
                                                                                      toolCallResponse
                                                                                              .getToolCallId());
                                                                             node.put("result",
                                                                                      toolCallResponse
                                                                                              .getResponse());
                                                                             return node;
                                                                         }

                                                                         @Override
                                                                         public JsonNode visit(UserPrompt userPrompt) {
                                                                             final var node = mapper.createObjectNode();
                                                                             node.put("type",
                                                                                      CompactMessage.Types.CHAT);
                                                                             node.put("role",
                                                                                      CompactMessage.Roles.USER);
                                                                             node.put("content",
                                                                                      userPrompt.getContent());
                                                                             return node;
                                                                         }

                                                                     });
                                                         }

                                                         @Override
                                                         public JsonNode visit(AgentResponse response) {
                                                             return response
                                                                     .accept(new AgentResponseVisitor<>() {

                                                                         @Override
                                                                         @SneakyThrows
                                                                         public JsonNode visit(StructuredOutput structuredOutput) {
                                                                             final var node = mapper
                                                                                     .createObjectNode();
                                                                             node.put("type",
                                                                                      CompactMessage.Types.CHAT);
                                                                             node.put("role",
                                                                                      CompactMessage.Roles.ASSISTANT);
                                                                             try {
                                                                                 final var jsonNode = mapper
                                                                                         .readTree(structuredOutput
                                                                                                 .getContent());
                                                                                 if (jsonNode.has(
                                                                                                  Agent.OUTPUT_VARIABLE_NAME)) {
                                                                                     node.put("content",
                                                                                              mapper.writeValueAsString(jsonNode
                                                                                                      .get(Agent.OUTPUT_VARIABLE_NAME)));
                                                                                 }
                                                                                 else {
                                                                                     node.put("content",
                                                                                              structuredOutput
                                                                                                      .getContent());
                                                                                 }
                                                                             }
                                                                             catch (JsonProcessingException e) {
                                                                                 log.error("Error parsing structured output content as JSON: %s"
                                                                                         .formatted(e.getMessage()), e);
                                                                                 node.put("content",
                                                                                          structuredOutput
                                                                                                  .getContent());
                                                                             }
                                                                             return node;
                                                                         }

                                                                         @Override
                                                                         public JsonNode visit(Text text) {
                                                                             final var node = mapper
                                                                                     .createObjectNode();
                                                                             node.put("type",
                                                                                      CompactMessage.Types.CHAT);
                                                                             node.put("role",
                                                                                      CompactMessage.Roles.ASSISTANT);
                                                                             node.put("content",
                                                                                      text.getContent());
                                                                             return node;
                                                                         }

                                                                         @Override
                                                                         public JsonNode visit(ToolCall toolCall) {
                                                                             final var node = mapper
                                                                                     .createObjectNode();
                                                                             node.put("type",
                                                                                      CompactMessage.Types.TOOL_CALL_RESPONSE);
                                                                             node.put("callId",
                                                                                      toolCall.getToolCallId());
                                                                             node.put("arguments",
                                                                                      toolCall.getArguments());
                                                                             return node;
                                                                         }
                                                                     });
                                                         }
                                                     });
            response.add(convertedNode);
        }
        return response;
    }
}
