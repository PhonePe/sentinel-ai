package com.phonepe.sentinel.session;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.phonepe.sentinel.session.history.History;
import com.phonepe.sentinel.session.history.HistoryStore;
import com.phonepe.sentinelai.core.agent.*;
import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.agentmessages.AgentMessageType;
import com.phonepe.sentinelai.core.utils.AgentUtils;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;


/**
 * Manages session for an agent
 */
@Value
@Builder
@Slf4j
public class AgentSessionExtension<R, T, A extends Agent<R, T, A>> implements AgentExtension<R, T, A> {
    private static final String OUTPUT_KEY = "sessionOutput";
    ObjectMapper mapper;
    SessionStore sessionStore;
    HistoryStore historyStore;
    AgentSessionExtensionSetup setup;


    public AgentSessionExtension(
            ObjectMapper mapper,
            SessionStore sessionStore,
            HistoryStore historyStore,
            @NonNull AgentSessionExtensionSetup setup) {
        this.mapper = Objects.requireNonNullElseGet(mapper, JsonUtils::createMapper);
        this.setup = setup;
        if (isSummaryEnabled()) {
            Objects.requireNonNull(sessionStore, "SessionStore cannot be null when SUMMARY mode is enabled");
        }
        if (isHistoryEnabled()) {
            Objects.requireNonNull(historyStore, "HistoryStore cannot be null when HISTORY mode is enabled");
        }
        this.sessionStore = sessionStore;
        this.historyStore = historyStore;

    }

    @Override
    public String name() {
        return "agent-session";
    }

    @Override
    public List<FactList> facts(
            R request,
            AgentRunContext<R> context,
            A agent) {
        final var sessionId = AgentUtils.sessionId(context);
        if (!Strings.isNullOrEmpty(sessionId)) {
            if (isSummaryEnabled()) {
                return sessionStore.session(sessionId)
                        .map(sessionSummary -> List.of(
                                new FactList("Information about session %s".formatted(sessionId),
                                        List.of(new Fact(
                                                "Session Summary", sessionSummary.toString())))))
                        .orElse(List.of());
            }

        }
        return List.of();
    }

    @Override
    public ExtensionPromptSchema additionalSystemPrompts(
            R request,
            AgentRunContext<R> context,
            A agent, ProcessingMode processingMode) {
        final var prompts = new ArrayList<SystemPrompt.Task>();
        if (isSummaryEnabled()) {
            final var prompt = SystemPrompt.Task.builder()
                    .objective("UPDATE SESSION SUMMARY")
                    .outputField(OUTPUT_KEY)
                    .instructions(
                            "Generate session summary and a list of topics being discussed in the session based on " +
                                    "the last few messages.");
            final var tools = this.tools();
            if (!tools.isEmpty()) {
                prompt.tool(tools.values()
                                    .stream()
                                    .map(tool -> SystemPrompt.ToolSummary.builder()
                                            .name(tool.getToolDefinition().getId())
                                            .description(tool.getToolDefinition().getDescription())
                                            .build())
                                    .toList());

            }
            prompts.add(prompt.build());
        }

        final var hints = new ArrayList<>();
        if (!Strings.isNullOrEmpty(AgentUtils.sessionId(context))) {
            hints.add("USE SESSION INFORMATION TO CONTEXTUALIZE RESPONSES");
        }
        return new ExtensionPromptSchema(prompts, hints);
    }

    @Override
    public Optional<ModelOutputDefinition> outputSchema(ProcessingMode processingMode) {
        if (isSummaryEnabled()) {
            return Optional.of(new ModelOutputDefinition(
                    OUTPUT_KEY,
                    "Schema summary for this session",
                    JsonUtils.schema(SessionSummary.class)));
        }
        return Optional.empty();
    }

    @Override
    public void consume(JsonNode output, A agent) {
        if (!isSummaryEnabled()) {
            return;
        }
        try {
            final var session = mapper.treeToValue(output, SessionSummary.class);
            final var updated = sessionStore.saveSession(agent.name(), session).orElse(null);
            log.info("Session summary: {}", updated);
        }
        catch (JsonProcessingException e) {
            log.error("Error converting json node to memory output. Error: %s Json: %s"
                              .formatted(e.getMessage(), output), e);
        }
    }


    @Override
    public List<AgentMessage> messages(R request, AgentRunContext<R> metadata, A agent) {
        if (isHistoryEnabled()) {
            final var sessionId = AgentUtils.sessionId(metadata);
            if (!Strings.isNullOrEmpty(sessionId)) {
                return historyStore.history(sessionId).map(History::toAgentMessages).orElse(List.of());

            }
        }
        return List.of();
    }

    @Override
    public void onExtensionRegistrationCompleted(A agent) {
        agent.onRequestCompleted().connect(this::extractAndStoreHistory);
    }

    private void extractAndStoreHistory(Agent.ProcessingCompletedData<R, T, A> data) {
        if (isHistoryEnabled()) {
            final var sessionId = AgentUtils.sessionId(data.getContext());
            if (!Strings.isNullOrEmpty(sessionId) || data.getOutput().getNewMessages().isEmpty()) {
                var messages = historyStore.history(sessionId).map(History::getMessages)
                        .orElse(Lists.newArrayList());


                AgentMessage latestPrompt = data.getOutput().getAllMessages().stream()
                        .filter(agentMessage -> agentMessage.getMessageType()
                                == AgentMessageType.USER_PROMPT_REQUEST_MESSAGE)
                        .reduce((first, second) -> second)
                        .orElse(null);
                var newMessages = data.getOutput().getNewMessages();
                if (!Objects.isNull(latestPrompt)) {
                    newMessages.add(latestPrompt);
                }
                messages.add(newMessages);
                historyStore.saveHistory(History.builder()
                        .sessionId(sessionId)
                        .messages(messages)
                        .build());
            }
        }
    }

    private boolean isSummaryEnabled() {
        return this.setup.getModes().contains(AgentSessionExtensionMode.SUMMARY);
    }

    private boolean isHistoryEnabled() {
        return this.setup.getModes().contains(AgentSessionExtensionMode.HISTORY);
    }
}
