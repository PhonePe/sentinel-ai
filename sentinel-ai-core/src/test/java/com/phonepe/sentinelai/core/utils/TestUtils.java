package com.phonepe.sentinelai.core.utils;

import com.github.tomakehurst.wiremock.http.Fault;
import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.agent.AgentOutput;
import com.phonepe.sentinelai.core.agentmessages.AgentMessageType;
import com.phonepe.sentinelai.core.agentmessages.requests.ToolCallResponse;
import com.phonepe.sentinelai.core.agentmessages.responses.ToolCall;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 */
@UtilityClass
public class TestUtils {
    public static void setupMocks(int numStates, String prefix, Class<?> clazz) {
        IntStream.rangeClosed(1, numStates)
                .forEach(i -> {
                    stubFor(post("/chat/completions?api-version=2024-10-21")
                                    .inScenario("model-test")
                                    .whenScenarioStateIs(i == 1 ? STARTED : Objects.toString(i))
                                    .willReturn(okForContentType("application/json",
                                                                 readStubFile(i, prefix, clazz)))
                                    .willSetStateTo(Objects.toString(i + 1)));

                });
    }

    public static void setupMocksWithFault(Fault fault) {
        stubFor(post("/chat/completions?api-version=2024-10-21")
                        .willReturn(aResponse()
                                            .withFault(fault)));
    }

    public static void setupMocksWithTimeout(Duration duration) {
        stubFor(post("/chat/completions?api-version=2024-10-21")
                        .willReturn(aResponse()
                                            .withStatus(200)
                                            .withFixedDelay((int) duration.toMillis())));
    }

    @SneakyThrows
    public static String readStubFile(int i, String prefix, Class<?> clazz) {
        return Files.readString(Path.of(Objects.requireNonNull(clazz.getResource(
                "/wiremock/%s.%d.json".formatted(prefix, i))).toURI()));
    }

    public static void assertNoFailedToolCalls(AgentOutput<String> response) {
        final var failedCall = response.getNewMessages().stream()
                .filter(agentMessage -> agentMessage.getMessageType()
                        .equals(AgentMessageType.TOOL_CALL_RESPONSE_MESSAGE))
                .map(ToolCallResponse.class::cast)
                .filter(Predicate.not(ToolCallResponse::isSuccess))
                .toList();
        assertTrue(failedCall.isEmpty(),
                   "Expected no failed tool calls, but found: " + failedCall.stream()
                           .map(ToolCallResponse::getToolName)
                           .toList());
    }

    public static void ensureOutputGenerated(final AgentOutput<?> response) {
        assertTrue(response.getNewMessages()
                           .stream()
                           .anyMatch(message -> message.getMessageType()
                                   .equals(AgentMessageType.TOOL_CALL_REQUEST_MESSAGE)
                                   && message instanceof ToolCall toolCall
                                   && toolCall.getToolName().equals(Agent.OUTPUT_GENERATOR_ID)),
                   "Expected at least one output function call, but found none.");
    }
}
