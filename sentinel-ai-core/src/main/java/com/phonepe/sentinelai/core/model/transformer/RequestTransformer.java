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

package com.phonepe.sentinelai.core.model.transformer;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;

/**
 * Transforms the outgoing request of a model call: the request body, the request itself or its
 * headers. Extensions contribute transformers per run; the model applies them before the provider
 * level and model level transformers.
 *
 * <p>A transformer that throws aborts the model call.
 */
@FunctionalInterface
public interface RequestTransformer {

    /**
     * Transforms one outgoing request.
     *
     * @param requestId the id of the run that produces the request; may be null
     * @param body      the request body; may be mutated in place
     * @param headers   mutable map of the request headers; entries are added or replaced here
     * @throws Exception when the transform fails; the model call aborts
     */
    void transform(String requestId, ObjectNode body, Map<String, String> headers) throws Exception;
}
