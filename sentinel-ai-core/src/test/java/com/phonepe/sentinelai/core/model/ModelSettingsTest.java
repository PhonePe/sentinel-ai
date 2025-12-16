package com.phonepe.sentinelai.core.model;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class ModelSettingsTest {

    @Test
    void mergeBothNullReturnsNull() {
        assertNull(ModelSettings.merge(null, null));
    }

    @Test
    void mergeLhsNullReturnsRhsReference() {
        final var rhs = ModelSettings.builder()
                .maxTokens(10)
                .temperature(0.5f)
                .topP(0.9f)
                .timeout(30f)
                .parallelToolCalls(Boolean.TRUE)
                .seed(123)
                .presencePenalty(0.1f)
                .frequencyPenalty(0.2f)
                .logitBias(Map.of("token", 5))
                .build();

        var merged = ModelSettings.merge(null, rhs);
        assertSame(rhs, merged);
    }

    @Test
    void mergeRhsNullReturnsLhsReference() {
        final var lhs = ModelSettings.builder()
                .maxTokens(20)
                .temperature(1.0f)
                .build();

        var merged = ModelSettings.merge(lhs, null);
        assertSame(lhs, merged);
    }

    @Test
    void mergeOverridesOnlyNonNullFields() {
        final var lhsMap = Map.of("a", 1, "b", 2);
        var rhsMap = Map.of("b", 3);

        final var lhs = ModelSettings.builder()
                .maxTokens(50)
                .temperature(0.1f)
                .topP(0.2f)
                .timeout(10f)
                .parallelToolCalls(Boolean.FALSE)
                .seed(42)
                .presencePenalty(0.3f)
                .frequencyPenalty(0.4f)
                .logitBias(lhsMap)
                .build();

        final var rhs = ModelSettings.builder()
                .maxTokens(100)
                .temperature(0.9f)
                .timeout(null)
                .parallelToolCalls(null)
                .seed(null)
                .presencePenalty(0.5f)
                .logitBias(rhsMap)
                .build();

        var merged = ModelSettings.merge(lhs, rhs);

        assertNotNull(merged);
        assertEquals(100, merged.getMaxTokens());
        assertEquals(0.9f, merged.getTemperature());
        assertEquals(0.2f, merged.getTopP());
        assertEquals(10f, merged.getTimeout());
        assertEquals(Boolean.FALSE, merged.getParallelToolCalls());
        assertEquals(42, merged.getSeed());
        assertEquals(0.5f, merged.getPresencePenalty());
        assertEquals(0.4f, merged.getFrequencyPenalty());
        assertSame(rhsMap, merged.getLogitBias());
    }

    @Test
    void mergeReturnsNewInstanceWhenBothNonNull() {
        final var lhs = ModelSettings.builder()
                .maxTokens(1)
                .build();
        final var rhs = ModelSettings.builder()
                .temperature(0.2f)
                .build();

        var merged = ModelSettings.merge(lhs, rhs);
        assertNotNull(merged);
        assertNotSame(lhs, merged);
        assertNotSame(rhs, merged);
        assertEquals(1, merged.getMaxTokens());
        assertEquals(0.2f, merged.getTemperature());
    }

    @Test
    void mergeEmptyLogitBiasTakesEmptyMap() {
        final var lhsMap = Map.of("x", 9);
        final var rhsMap = Map.<String, Integer>of();

        final var lhs = ModelSettings.builder()
                .logitBias(lhsMap)
                .build();

        final var rhs = ModelSettings.builder()
                .logitBias(rhsMap)
                .build();

        var merged = ModelSettings.merge(lhs, rhs);
        assertSame(rhsMap, merged.getLogitBias());
    }

    @Test
    void mergeWithAllRhsFieldsNullKeepsLhsValues() {
        final var lhs = ModelSettings.builder()
                .maxTokens(7)
                .temperature(0.7f)
                .topP(0.3f)
                .timeout(5f)
                .parallelToolCalls(Boolean.TRUE)
                .seed(77)
                .presencePenalty(0.11f)
                .frequencyPenalty(0.22f)
                .logitBias(Map.of("k", 1))
                .build();

        final var rhs = ModelSettings.builder()
                .maxTokens(null)
                .temperature(null)
                .topP(null)
                .timeout(null)
                .parallelToolCalls(null)
                .seed(null)
                .presencePenalty(null)
                .frequencyPenalty(null)
                .logitBias(null)
                .build();

        var merged = ModelSettings.merge(lhs, rhs);
        assertEquals(7, merged.getMaxTokens());
        assertEquals(0.7f, merged.getTemperature());
        assertEquals(0.3f, merged.getTopP());
        assertEquals(5f, merged.getTimeout());
        assertEquals(Boolean.TRUE, merged.getParallelToolCalls());
        assertEquals(77, merged.getSeed());
        assertEquals(0.11f, merged.getPresencePenalty());
        assertEquals(0.22f, merged.getFrequencyPenalty());
        assertSame(lhs.getLogitBias(), merged.getLogitBias());
    }
}
