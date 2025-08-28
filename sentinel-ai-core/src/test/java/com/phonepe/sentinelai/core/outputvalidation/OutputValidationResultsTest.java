package com.phonepe.sentinelai.core.outputvalidation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OutputValidationResultsTest {

    @Test
    void testDefaultConstructorHasNoFailures() {
        final var results = OutputValidationResults.success();
        assertNotNull(results.getFailures());
        assertTrue(results.getFailures().isEmpty());
    }

    @Test
    void testConstructorWithFailures() {
        final var failure =
                new OutputValidationResults.ValidationFailure(OutputValidationResults.FailureType.RETRYABLE, "msg");
        final var results = new OutputValidationResults(List.of(failure));
        assertEquals(1, results.getFailures().size());
        assertEquals(failure, results.getFailures().get(0));
    }

    @Test
    void testHasFailures() {
        final var emptyResults = new OutputValidationResults();
        assertTrue(emptyResults.isSuccessful());
        final var failure =
                new OutputValidationResults.ValidationFailure(OutputValidationResults.FailureType.PERMANENT, "fail");
        final var results = new OutputValidationResults(List.of(failure));
        assertFalse(results.isSuccessful());
        assertFalse(results.isRetriable());
    }

    @Test
    void testAddFailure() {
        final var results = new OutputValidationResults();
        results.addFailure("error");
        assertEquals(1, results.getFailures().size());
        assertEquals(OutputValidationResults.FailureType.RETRYABLE, results.getFailures().get(0).getType());
        assertEquals("error", results.getFailures().get(0).getMessage());
    }

    @Test
    void testAddFailures() {
        final var results = new OutputValidationResults()
                .addFailures(List.of("fail1", "fail2"))
                .addFailure("fail3");
        assertEquals(3, results.getFailures().size());
    }

    @Test
    void testFailuresFunction() {
        final var results = OutputValidationResults.failure("fail1", "fail2");
        assertEquals(2, results.getFailures().size());
    }
}

