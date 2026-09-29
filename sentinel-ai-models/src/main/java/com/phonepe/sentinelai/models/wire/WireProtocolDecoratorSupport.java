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

package com.phonepe.sentinelai.models.wire;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * Base for protocol decorators. Implements every {@link WireProtocol} method by delegation to
 * {@link #delegate()}; subclasses override only the hooks they need.
 * <p>
 * Hooks run in this order: request hooks after the delegate builds the body; response and stream
 * hooks before the delegate decodes. Decorators transform only the final request body per turn;
 * they never touch the translated-message buffer.
 */
public abstract class WireProtocolDecoratorSupport implements WireProtocol {

    @Override
    public void applyExtras(final ObjectNode body, final JsonNode extras) {
        delegate().applyExtras(body, transformExtras(extras));
    }

    @Override
    public ObjectNode buildRequestBody(final WireContext ctx, final List<JsonNode> messages) {
        return transformRequest(ctx, delegate().buildRequestBody(ctx, messages));
    }

    @Override
    public com.phonepe.sentinelai.core.errors.ErrorType classifyError(final int status, final JsonNode errorBody) {
        return delegate().classifyError(status, errorBody);
    }

    @Override
    public WireResponse decodeResponse(final WireContext ctx, final JsonNode body) {
        return delegate().decodeResponse(ctx, transformResponse(body));
    }

    @Override
    public WireStreamEvent decodeStreamEvent(final WireContext ctx, final SseEvent event) {
        return delegate().decodeStreamEvent(ctx, transformStreamEvent(event));
    }


    @Override
    public com.phonepe.sentinelai.models.wire.MessageCodec messageCodec() {
        return delegate().messageCodec();
    }

    /**
     * @return The protocol being decorated.
     */
    protected abstract WireProtocol delegate();

    /**
     * Transforms the extras node before the delegate merges it. Default: no-op.
     */
    protected JsonNode transformExtras(final JsonNode extras) {
        return extras;
    }

    /**
     * Transforms the request body after the delegate built it. Default: no-op.
     */
    protected ObjectNode transformRequest(final WireContext ctx, final ObjectNode body) {
        return body;
    }

    /**
     * Transforms the response body before the delegate decodes it. Default: no-op.
     */
    protected JsonNode transformResponse(final JsonNode body) {
        return body;
    }

    /**
     * Transforms one SSE event before the delegate decodes it. Default: no-op.
     */
    protected SseEvent transformStreamEvent(final SseEvent event) {
        return event;
    }
}
