package com.phonepe.sentinelai.models;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.phonepe.sentinelai.core.agent.*;
import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.model.ModelSettings;
import com.phonepe.sentinelai.core.model.ModelUsageStats;
import com.phonepe.sentinelai.core.model.OutputGenerationMode;
import com.phonepe.sentinelai.core.tools.Tool;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.core.utils.TestUtils;
import io.github.sashirestela.cleverclient.client.OkHttpClientAdapter;
import io.github.sashirestela.openai.SimpleOpenAIAzure;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.phonepe.sentinelai.core.utils.TestUtils.readStubFile;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests simple text based io with {@link SimpleOpenAIModel}
 */
@Slf4j
@WireMockTest
class SimpleOpenAIModelStreamingTest {
    private static final class TestAgent extends Agent<String, String, TestAgent> {

        public TestAgent(
                @NonNull AgentSetup setup) {
            super(String.class,
                  "Greet the user by name and respond to queries",
                  setup,
                  List.of(),
                  Map.of());
        }

        @Override
        public String name() {
            return "test-agent";
        }

        @Tool("Get name of the user")
        public String getName() {
            return "Santanu";
        }

        @Tool("Get location of the user")
        public String getLocation(@JsonPropertyDescription("User name") String name) {
            if(name.equalsIgnoreCase("santanu")) {
                return "Bangalore";
            }
            throw new IllegalArgumentException("Invalid parameter");
        }

        @Tool("Get weather for city")
        public String getWeather(@JsonPropertyDescription("City name") String city) {
            if(city.equalsIgnoreCase("bangalore")) {
                return "Sunny";
            }
            throw new IllegalArgumentException("Invalid parameter");
        }
    }

    @Test
    @SneakyThrows
    void testAgent(final WireMockRuntimeInfo wiremock) {
        //Setup stub for SSE
        IntStream.rangeClosed(1, 5)
                .forEach(i -> {
                    stubFor(post("/chat/completions?api-version=2024-10-21")
                                    .inScenario("model-test")
                                    .whenScenarioStateIs(i == 1 ? Scenario.STARTED : Objects.toString(i))
                                    .willReturn(okForContentType("text/event-stream",
                                                                 readStubFile(i, "events", getClass())))
                                    .willSetStateTo(Objects.toString(i + 1)));

                });
        final var objectMapper = JsonUtils.createMapper();

        final var stats = new ModelUsageStats(); //We want to collect stats from the whole session
        final var executor = Executors.newCachedThreadPool();
        final var httpClient = new OkHttpClient.Builder()
                .build();
        final var agent = setupAgent(wiremock, objectMapper, httpClient, executor);
        final var outputStream = new PrintStream(new FileOutputStream("/dev/stdout"), true);
        final var response = agent.executeAsyncStreaming(AgentInput.<String>builder()
                                                                 .request("Hi")
                                                                 .requestMetadata(
                                                                         AgentRequestMetadata.builder()
                                                                                 .sessionId("s1")
                                                                                 .userId("ss")
                                                                                 .usageStats(stats)
                                                                                 .build())
                                                                 .build(),
                                                         new TextStreamer(objectMapper, executor, data -> print(data, outputStream)))
                .join();
        var responseString = response.getData();
        log.info("Agent response: {}", responseString);
        assertNotNull(responseString);
        //The following needs to be done because the model is not deterministic and might call tools at different times
        // across runs
        final var sunnyFound = new AtomicBoolean(responseString.contains("sunny"));
        final var nameFound = new AtomicBoolean(responseString.contains("Santanu"));
        assertTrue(response.getUsage().getTotalTokens() > 1); //This ensures that all chunks have been consumed
        final var response2 = agent.executeAsyncTextStreaming(
                        AgentInput.<String>builder()
                                .request("How is the weather?")
                                .requestMetadata(
                                        AgentRequestMetadata.builder()
                                                .sessionId("s1")
                                                .userId("ss")
                                                .usageStats(stats)
                                                .build())
                                .oldMessages(response.getAllMessages())
                                .build(),
                        new TextStreamer(objectMapper, executor, data -> print(data, outputStream)))
                .join();
        responseString = response2.getData();
        log.info("Agent response: {}", responseString);
        sunnyFound.compareAndSet(false, responseString.contains("sunny"));
        nameFound.compareAndSet(false, responseString.contains("Santanu"));
        assertTrue(sunnyFound.get() && nameFound.get());
        assertTrue(response2.getUsage().getTotalTokens() > 1);
        assertTrue(stats.getTotalTokens() > 1);
        log.info("Session stats: {}", stats);
    }

    @Test
    @SneakyThrows
    void testTimeouts(final WireMockRuntimeInfo wiremock) {
        TestUtils.setupMocksWithTimeout(Duration.ofSeconds(1));

        final var httpClient = new OkHttpClient.Builder()
                .readTimeout(Duration.ofMillis(100))
                .build();

        final var response = execute(wiremock, httpClient);
        assertSame(ErrorType.MODEL_CALL_COMMUNICATION_ERROR,
                response.getError().getErrorType(),
                "Expected TIMEOUT after retries, got: " + response.getError());
    }

    private static AgentOutput<String> execute(final WireMockRuntimeInfo wiremock,
                                               final OkHttpClient client) throws FileNotFoundException {
        final var objectMapper = JsonUtils.createMapper();

        final var stats = new ModelUsageStats(); //We want to collect stats from the whole session
        final var executor = Executors.newCachedThreadPool();
        final var agent = setupAgent(wiremock, objectMapper, client, executor);
        final var outputStream = new PrintStream(new FileOutputStream("/dev/stdout"), true);
        return agent.executeAsyncStreaming(AgentInput.<String>builder()
                                .request("Hi")
                                .requestMetadata(
                                        AgentRequestMetadata.builder()
                                                .sessionId("s1")
                                                .userId("ss")
                                                .usageStats(stats)
                                                .build())
                                .build(),
                        new TextStreamer(objectMapper, executor, data -> print(data, outputStream)))
                .join();
    }

    private static void print(byte[] data, PrintStream outputStream) {
        try {
            outputStream.write(data);
            outputStream.flush();
            log.info("RECEIVED: {}", new String(data));
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static TestAgent setupAgent(final WireMockRuntimeInfo wiremock,
                                        final JsonMapper objectMapper,
                                        final OkHttpClient httpClient,
                                        final ExecutorService executor) {
        final var model = new SimpleOpenAIModel<>(
                "gpt-4o",
                SimpleOpenAIAzure.builder()
//                        .baseUrl("http://localhost:8080") //Wiremock recorder input: $AZURE_ENDPOINT
//                        // Uncomment the above to record responses using the wiremock recorder.
//                        // Yeah ... life is hard
//                        .baseUrl(EnvLoader.readEnv("AZURE_ENDPOINT"))
//                        .apiKey(EnvLoader.readEnv("AZURE_API_KEY"))
                        .baseUrl(wiremock.getHttpBaseUrl())
                        .apiKey("BLAH")
                        .apiVersion("2024-10-21")
                        .objectMapper(objectMapper)
                        .clientAdapter(new OkHttpClientAdapter(httpClient))
                        .build(),
                objectMapper,
                SimpleOpenAIModelOptions.builder()
                        .toolChoice(SimpleOpenAIModelOptions.ToolChoice.AUTO)
                        .build()
        );
        return new TestAgent(AgentSetup.builder()
                .model(model)
                .mapper(objectMapper)
                .modelSettings(ModelSettings.builder()
                        .parallelToolCalls(false)
                        .temperature(0.1f)
                        .seed(1)
                        .build())
                .executorService(executor)
                .outputGenerationMode(OutputGenerationMode.STRUCTURED_OUTPUT)
                .build());
    }
}
