package com.phonepe.sentinelai.core.utils;

import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.IntStream;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;

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
                                                                 readFile(i, prefix, clazz)))
                                    .willSetStateTo(Objects.toString(i + 1)));

                });
    }

    @SneakyThrows
    private static String readFile(int i, String prefix, Class<?> clazz) {
        return Files.readString(Path.of(Objects.requireNonNull(clazz.getResource(
                "/wiremock/%s.%d.json".formatted(prefix, i))).toURI()));
    }
}
