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

package com.phonepe.sentinelai.models.transport;

import java.util.Map;

/**
 * Value object holding the raw HTTP response of a blocking model call. The body is the raw
 * response bytes. Error classification (status code, error JSON) is done by the caller using
 * the wire protocol.
 */
public record TransportResponse(
        int status,
        Map<String, String> headers,
        byte[] body
) {

    /**
     * @return True if the HTTP status indicates success (2xx).
     */
    public boolean isSuccessful() {
        return status >= 200 && status < 300;
    }
}
