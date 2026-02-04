/*
 * Copyright (c) 2025 Original Author(s), PhonePe India Pvt. Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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