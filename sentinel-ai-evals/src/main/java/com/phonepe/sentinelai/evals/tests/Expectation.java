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

package com.phonepe.sentinelai.evals.tests;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Abstract base class representing the <em>definition</em> of an expectation.
 *
 * An Expectation carries only the configuration data that describes WHAT to assert
 * (e.g. an expected substring, a JSON-Path expression, a metric threshold). All
 * computation is delegated to a corresponding {@link ExpectationExecutor} created
 * by an {@link ExpectationExecutorFactory}.
 *
 * @param <R> result/output type being evaluated
 * @param <T> input/request type
 */
@Getter
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class Expectation<R, T> {

    private final String id;
}
