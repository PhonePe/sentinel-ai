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

package com.phonepe.sentinelai.configuredagents;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link SimpleCache}.
 */
class SimpleCacheTest {

    private record TestObject(String name) {
        public static TestObject create(String name) {
            return new TestObject(name);
        }
    }

    @Test
    void test() {
        final var cache = new SimpleCache<>(TestObject::create);
        final var res = cache.find("test");
        assertTrue(res.isPresent());
        assertEquals("test", res.get().name());

        assertThrowsExactly(NullPointerException.class, () -> cache.find(null));
    }
}
