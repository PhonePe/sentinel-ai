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

package com.phonepe.sentinelai.evals.tests.expectations.executors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;

import com.phonepe.sentinelai.core.events.AgentEvent;
import com.phonepe.sentinelai.core.events.ToolCallCompletedAgentEvent;
import com.phonepe.sentinelai.core.events.ToolCalledAgentEvent;
import com.phonepe.sentinelai.evals.AgentEventTracer;
import com.phonepe.sentinelai.evals.EvalStatus;
import com.phonepe.sentinelai.evals.ExpectationReport;
import com.phonepe.sentinelai.evals.tests.EvalExpectationContext;
import com.phonepe.sentinelai.evals.tests.ExpectationExecutor;
import com.phonepe.sentinelai.evals.tests.expectations.AgentEventExpectation;
import com.phonepe.sentinelai.evals.tests.expectations.Operator;

import lombok.RequiredArgsConstructor;
import lombok.val;

import java.time.Duration;
import java.util.ArrayList;

@RequiredArgsConstructor
public class AgentEventExpectationExecutor<R, T> implements ExpectationExecutor<R, T> {

    private final AgentEventExpectation<R, T> expectation;
    private final AgentEventTracer tracer;
    private final ObjectMapper objectMapper;

    private static boolean matchesAgentName(AgentEvent event, String agentName) {
        return agentName == null || agentName.isEmpty() || agentName.equals(event.getAgentName());
    }

    private static boolean matchesEventKey(AgentEvent event, String eventKey) {
        if (event instanceof ToolCallCompletedAgentEvent e) {
            return eventKey.equals(e.getToolCallName());
        }
        if (event instanceof ToolCalledAgentEvent e) {
            return eventKey.equals(e.getToolCallName());
        }
        return false;
    }

    private static Object normalizeValue(Object value) {
        if (value instanceof Duration d) {
            return d.toMillis();
        }
        return value;
    }

    @Override
    public boolean evaluate(R result, EvalExpectationContext<T> context) {
        return evaluateWithReport(result, context).getStatus() == EvalStatus.PASSED;
    }

    @Override
    public ExpectationReport evaluateWithReport(R result, EvalExpectationContext<T> context) {
        val events = tracer.findEvents(e -> {
            if (!matchesAgentName(e, expectation.getAgentName())) {
                return false;
            }
            if (e.getType() != expectation.getEventType()) {
                return false;
            }
            if (expectation.getEventKey() == null || expectation.getEventKey().isEmpty()) {
                return true;
            }
            return matchesEventKey(e, expectation.getEventKey());
        });

        if (events.isEmpty()) {
            return ExpectationReport.passFail(expectation.id(), false, "no matching events found");
        }

        boolean allPassed = true;
        val details = new ArrayList<String>();
        for (val event : events) {
            // If no jsonPath/expectedValue/operator are specified, just verify
            // matching events exist (count > 0 is sufficient).
            if (expectation.getJsonPath() == null && expectation.getExpectedValue() == null) {
                details.add("event matched");
                continue;
            }
            Object actualValue = extractField(event, expectation.getJsonPath());
            if (actualValue == null) {
                allPassed = false;
                details.add("field not found");
                continue;
            }
            val normalizedActual = normalizeValue(actualValue);
            val normalizedExpected = normalizeValue(expectation.getExpectedValue());
            val operator = expectation.getOperator() != null ? expectation.getOperator() : Operator.GT;
            boolean passed = operator.compare(normalizedActual, normalizedExpected);
            if (!passed) {
                allPassed = false;
            }
            details.add(String.format("value=%s (expected %s %s)",
                                      normalizedActual,
                                      operator.name(),
                                      normalizedExpected));
        }

        return ExpectationReport.passFail(expectation.id(), allPassed, String.join("; ", details));
    }

    private Object extractField(AgentEvent event, String jsonPath) {
        try {
            if (jsonPath == null || jsonPath.isEmpty() || "$.".equals(jsonPath)) {
                return event;
            }
            val document = objectMapper.convertValue(event, Object.class);
            return JsonPath.read(document, jsonPath);
        }
        catch (Exception e) {
            return null;
        }
    }
}
