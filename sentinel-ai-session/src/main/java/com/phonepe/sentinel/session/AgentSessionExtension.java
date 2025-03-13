package com.phonepe.sentinel.session;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.agent.AgentExtension;
import com.phonepe.sentinelai.core.agent.AgentRequestMetadata;
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
public class AgentSessionExtension implements AgentExtension {
    private static final String OUTPUT_KEY = "sessionOutput";
    ObjectMapper mapper;
    SessionStore sessionStore;
    boolean updateSummaryAfterSession;

    public AgentSessionExtension(ObjectMapper mapper,
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
    public <R, D, T, A extends Agent<R, D, T, A>> List<String> additionalSystemPrompts(
            R request,
            AgentRequestMetadata metadata,
            A agent) {
        final var prompts = new ArrayList<String>();
        if(updateSummaryAfterSession) {
            prompts.add("""
                        ## UPDATE SESSION SUMMARY
                         Generate session summary and a list of topics being discussed in the session based on the last few messages.
                        """);
        }

        if (!Strings.isNullOrEmpty(metadata.getSessionId())) {
            prompts.add("### SUMMARY OF THE CURRENT SESSION:");
            sessionStore.session(metadata.getSessionId())
                    .ifPresent(session -> {
                        prompts.add(" - Summary of discussions in current session: " + session.getSummary());
                        prompts.add(" - Topics being discussed in session: "
                                            + Joiner.on(",").join(session.getTopics()));
                    });
        }
        return prompts;
    }

    @Override
    public Optional<AgentExtensionOutputDefinition> outputSchema() {
        if(updateSummaryAfterSession) {
            return Optional.of(new AgentExtensionOutputDefinition(
                    OUTPUT_KEY,
                    "Schema summary for this session",
                    JsonUtils.schema(SessionSummary.class)));
        }
        return Optional.empty();
    }

    @Override
    public <R, D, T, A extends Agent<R, D, T, A>> void consume(JsonNode output, A agent) {
        if(!updateSummaryAfterSession) {
            return;
        }
        try {
            final var session = mapper.treeToValue(output, SessionSummary.class);
            final var updated = sessionStore.saveSession(session).orElse(null);
            log.info("Session summary: {}", updated);
        }
        catch (JsonProcessingException e) {
            log.error("Error converting json node to memory output. Error: %s Json: %s"
                              .formatted(e.getMessage(), output), e);
        }
    }
}
