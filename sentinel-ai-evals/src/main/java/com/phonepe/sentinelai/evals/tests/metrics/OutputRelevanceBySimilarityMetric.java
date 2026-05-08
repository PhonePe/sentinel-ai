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

package com.phonepe.sentinelai.evals.tests.metrics;

/**
 * Definition for a metric that measures semantic relevance between output and input.
 *
 * This is a pure marker/config class — it carries no parameters.
 * The embedding model required for computation is provided via the executor
 * (wired through {@link MetricExecutorRegistry}).
 *
 * @param <T> input/request type
 */
public class OutputRelevanceBySimilarityMetric<T> implements Metric<String, T> {

    /**
     * Returns the stable display name of this metric.
     *
     * @return metric name
     */
    @Override
    public String metricName() {
        return "OutputRelevance";
    }
}
