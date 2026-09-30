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

import lombok.Getter;

import java.time.Duration;

/**
 * Non-2xx HTTP status of a model call. Carries the status code so the retry layer can decide
 * whether the call is retryable without parsing the message.
 */
@Getter
public class ModelHttpException extends RuntimeException {

    private final transient int status;

    /**
     * Retry-After delay of the failed response in seconds; null when the response does not
     * carry the header or the header value is not a number.
     */
    private final transient Duration retryAfterSeconds;

    protected ModelHttpException(final int status, final String message) {
        this(status, message, null);
    }

    protected ModelHttpException(final int status, final String message, final Duration retryAfterSeconds) {
        super(message);
        this.status = status;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    /**
     * Parses the {@code Retry-After} header value in seconds; a negative or invalid value
     * returns null.
     *
     * @param retryAfter the header value; may be null
     * @return the parsed delay, or null when the header is absent or invalid
     */
    public static Duration parseRetryAfter(final String retryAfter) {
        if (retryAfter == null || retryAfter.isBlank()) {
            return null;
        }
        try {
            final var seconds = Long.parseLong(retryAfter.trim());
            return seconds < 0 ? null : Duration.ofSeconds(seconds);
        }
        catch (NumberFormatException e) {
            return null;
        }
    }
}
