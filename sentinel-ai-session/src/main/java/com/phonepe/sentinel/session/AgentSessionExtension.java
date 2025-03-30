package com.phonepe.sentinel.session;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.agent.AgentExtension;
import com.phonepe.sentinelai.core.agent.AgentRequestMetadata;
import com.phonepe.sentinelai.core.agent.SystemPromptSchema;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;

/**
 * Manages session for an agent
 */
@Value
@Builder
@Slf4j
public class AgentSessionExtension implements AgentExtension {
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
    public <R, T, A extends Agent<R, T, A>> ExtensionPromptSchema additionalSystemPrompts(
            R request,
            AgentRequestMetadata metadata,
            A agent) {
        final var prompts = new ArrayList<SystemPromptSchema.SecondaryTask>();
        if (updateSummaryAfterSession) {
            final var prompt = new SystemPromptSchema.SecondaryTask()
                    .setObjective("UPDATE SESSION SUMMARY")
                    .setOutputField(OUTPUT_KEY)
                    .setInstructions(
                            "Generate session summary and a list of topics being discussed in the session based on " +
                                    "the last few messages.");
            final var tools = this.tools();
            if(!tools.isEmpty()) {
                prompt.setTools(tools.values()
                                        .stream()
                                        .map(tool -> new SystemPromptSchema.ToolSummary()
                                                .setName(tool.getToolDefinition().getName())
                                                .setDescription(tool.getToolDefinition().getDescription()))
                                        .toList());

            }
            prompts.add(prompt);
        }

        final var hints = new ArrayList<>();
        if (!Strings.isNullOrEmpty(metadata.getSessionId())) {
            hints.add("USE SESSION INFORMATION TO CONTEXTUALIZE RESPONSES");
            sessionStore.session(metadata.getSessionId())
                    .ifPresent(hints::add);
        }
        return new ExtensionPromptSchema(prompts, hints);
    }

    @Override
    public Optional<AgentExtensionOutputDefinition> outputSchema() {
        if (updateSummaryAfterSession) {
            return Optional.of(new AgentExtensionOutputDefinition(
                    OUTPUT_KEY,
                    "Schema summary for this session",
                    JsonUtils.schema(SessionSummary.class)));
        }
        return Optional.empty();
    }

    @Override
    public <R, T, A extends Agent<R, T, A>> void consume(JsonNode output, A agent) {
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
}
