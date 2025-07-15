package com.phonepe.sentinel.session;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.phonepe.sentinelai.core.agent.*;
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
    boolean updateSummaryAfterSession;

    public AgentSessionExtension(
            ObjectMapper mapper,
            @NonNull SessionStore sessionStore,
            boolean updateSummaryAfterSession) {
        this.mapper = Objects.requireNonNullElseGet(mapper, JsonUtils::createMapper);
        this.sessionStore = sessionStore;
        this.updateSummaryAfterSession = updateSummaryAfterSession;
    }

    @Override
    public String name() {
        return "agent-session";
    }

    @Override
    public List<FactList> facts(
            R request,
            AgentRequestMetadata metadata,
            A agent) {
        if (!Strings.isNullOrEmpty(metadata.getSessionId())) {
            return sessionStore.session(metadata.getSessionId())
                    .map(sessionSummary -> List.of(
                            new FactList("Information about session %s".formatted(metadata.getSessionId()),
                                         List.of(new Fact(
                                                 "Session Summary", sessionSummary.toString())))))
                    .orElse(List.of());
        }
        return List.of();
    }

    @Override
    public ExtensionPromptSchema additionalSystemPrompts(
            R request,
            AgentRequestMetadata metadata,
            A agent, ProcessingMode processingMode) {
        final var prompts = new ArrayList<SystemPrompt.Task>();
        if (updateSummaryAfterSession) {
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
        if (!Strings.isNullOrEmpty(metadata.getSessionId())) {
            hints.add("USE SESSION INFORMATION TO CONTEXTUALIZE RESPONSES");
        }
        return new ExtensionPromptSchema(prompts, hints);
    }

    @Override
    public Optional<AgentExtensionOutputDefinition> outputSchema(ProcessingMode processingMode) {
        if (updateSummaryAfterSession) {
            return Optional.of(new AgentExtensionOutputDefinition(
                    OUTPUT_KEY,
                    "Schema summary for this session",
                    JsonUtils.schema(SessionSummary.class)));
        }
        return Optional.empty();
    }

    @Override
    public void consume(JsonNode output, A agent) {
        if (!updateSummaryAfterSession) {
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
    public void onExtensionRegistrationCompleted(A agent) {
        //Nothing to do here for now
    }
}
