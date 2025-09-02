package com.phonepe.sentinelai.core.outputvalidation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests {@link CompositeOutputValidator}
 */
class CompositeOutputValidatorTest {

    @Test
    void test() {
        final var validator = new CompositeOutputValidator<>()
                .addValidator((ctx,out) -> OutputValidationResults.success());
        assertTrue(validator.validate(null, null).isSuccessful());
        validator.addValidator((ctx,out) -> OutputValidationResults.failure("Fail"));
        assertFalse(validator.validate(null, null).isSuccessful());
        assertThrows(NullPointerException.class, () -> new CompositeOutputValidator<>(null));
    }
}