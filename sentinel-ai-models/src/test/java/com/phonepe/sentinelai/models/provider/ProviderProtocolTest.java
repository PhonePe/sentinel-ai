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

package com.phonepe.sentinelai.models.provider;

import org.junit.jupiter.api.Test;

import com.phonepe.sentinelai.models.openai.ChatCompletionsProtocol;
import com.phonepe.sentinelai.models.openai.ResponsesProtocol;
import com.phonepe.sentinelai.models.wire.WireProtocol;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests of {@link Provider#protocolFor(WireProtocol)}: default resolution, supported overrides
 * and the fail-fast rejection of unsupported protocols.
 */
class ProviderProtocolTest {

    private static final WireProtocol CHAT = new ChatCompletionsProtocol();
    private static final WireProtocol RESPONSES = new ResponsesProtocol();

    @Test
    void defaultProtocolIsAlwaysSupported() {
        final var provider = Provider.builder()
                .baseUrl("http://localhost")
                .protocol(RESPONSES)
                .build();
        assertSame(RESPONSES, provider.protocolFor(RESPONSES));
    }

    @Test
    void nullRequestReturnsDefaultProtocol() {
        final var provider = Provider.builder()
                .baseUrl("http://localhost")
                .protocol(CHAT)
                .build();
        assertSame(CHAT, provider.protocolFor(null));
    }

    @Test
    void supportedOverrideIsReturned() {
        final var provider = Provider.builder()
                .baseUrl("http://localhost")
                .protocol(CHAT)
                .supportedProtocols(List.of(RESPONSES))
                .build();
        assertSame(RESPONSES, provider.protocolFor(RESPONSES));
        assertSame(CHAT, provider.protocolFor(CHAT));
    }

    @Test
    void unsupportedOverrideFailsFast() {
        final var provider = Provider.builder()
                .baseUrl("http://localhost")
                .protocol(CHAT)
                .build();
        final var error = assertThrows(IllegalArgumentException.class,
                                       () -> provider.protocolFor(RESPONSES));
        assertTrue(error.getMessage().contains("not supported"));
    }
}
