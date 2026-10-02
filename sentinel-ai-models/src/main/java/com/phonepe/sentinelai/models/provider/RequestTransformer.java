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

import com.fasterxml.jackson.databind.node.ObjectNode;

import okhttp3.Request;

/**
 * Transforms one outgoing model request after the protocol builds the body and the provider
 * authentication is applied, and before the request is serialized and sent. Transformers can
 * mutate the request body (for example vendor-specific payload fields), add headers, or
 * adjust the request itself.
 *
 * <p>Transformers are declared at three levels: on the {@link Provider}, on the model, and
 * per run through the agent extensions. The model applies them in that order; each
 * transformer sees the output of the previous one.
 *
 * <p>A transformer that throws aborts the model call; the failure is reported as a
 * {@code REQUEST_TRANSFORM_FAILED} error.
 */
@FunctionalInterface
public interface RequestTransformer {

    /**
     * Transforms one outgoing request; the final mutated body is what is serialized and sent.
     * A transformer that throws aborts the call with {@code REQUEST_TRANSFORM_FAILED}.
     */
    void transform(Request.Builder requestBuilder, ObjectNode body, RequestTransformerContext ctx) throws Exception;
}
