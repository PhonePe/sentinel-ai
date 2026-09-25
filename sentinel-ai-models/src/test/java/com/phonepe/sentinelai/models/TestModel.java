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

package com.phonepe.sentinelai.models;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.phonepe.sentinelai.models.transport.ModelTransport;
import com.phonepe.sentinelai.models.wire.TestWireProtocol;
import com.phonepe.sentinelai.models.wire.WireProtocol;

/**
 * Concrete {@link AbstractModel} for tests. Uses the test wire protocol; the real protocol
 * implementations land in later phases.
 */
public class TestModel extends AbstractModel {

    private final String baseUrl;

    public TestModel(final String modelName,
                     final WireProtocol protocol,
                     final ModelTransport transport,
                     final ObjectMapper mapper,
                     final ModelOptions modelOptions,
                     final String baseUrl) {
        super(modelName, protocol, transport, mapper, modelOptions, null);
        this.baseUrl = baseUrl;
    }

    public static TestModel of(final String modelName,
                               final String baseUrl,
                               final ObjectMapper mapper,
                               final ModelTransport transport,
                               final ModelOptions modelOptions) {
        return new TestModel(modelName,
                             new TestWireProtocol(mapper, modelOptions),
                             transport,
                             mapper,
                             modelOptions,
                             baseUrl);
    }

    @Override
    protected String baseUrl() {
        return baseUrl;
    }
}
