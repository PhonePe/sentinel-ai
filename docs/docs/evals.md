---
title: Evals
description: Evaluate Sentinel AI agents with datasets, expectations, and reports
---

# Evals

`sentinel-evals` provides a lightweight way to run repeatable evaluation suites against Sentinel AI agents.

`EvalEngine` is generic and supports any request type: `Agent<R, T, A>`.

Use it to:

- validate functional behavior before deployments,
- check tool-calling flows,
- run sampled regression suites in CI,
- and inspect pass/fail/skipped outcomes with per-test details.

## Add dependency

If you already use the Sentinel BOM, add:

```xml
<dependency>
    <groupId>com.phonepe.sentinel-ai</groupId>
    <artifactId>sentinel-evals</artifactId>
</dependency>
```

## Core concepts

- `Dataset<R, T>`: named collection of test cases.
- `TestCase<R, T>`: input + expectations (+ optional timeout).
- `Expectation`: predicate evaluated against agent output and run context.
- `EvalEngine`: executes a dataset against an agent.
- `EvalRunConfig`: controls sampling, fail-fast, and default timeout.
- `EvalReport`: aggregate execution summary + per-test reports.

## Quickstart

```java
import com.phonepe.sentinelai.evals.EvalEngine;
import com.phonepe.sentinelai.evals.EvalReport;
import com.phonepe.sentinelai.evals.tests.Dataset;
import com.phonepe.sentinelai.evals.tests.Expectations;
import com.phonepe.sentinelai.evals.tests.TestCase;

final var dataset = new Dataset("smoke-suite",
                                List.of(
                                        new TestCase("Say hello",
                                                     List.of(Expectations.outputContains("hello"))),
                                        new TestCase("Return status",
                                                     List.of(Expectations.jsonPathEquals("$.status", "SUCCESS")))));

EvalReport report = new EvalEngine().run(dataset, agent);

System.out.printf("Executed=%d Passed=%d Failed=%d Skipped=%d%n",
                  report.getExecutedTestCases(),
                  report.getPassedTestCases(),
                  report.getFailedTestCases(),
                  report.getSkippedTestCases());
```

## Built-in expectations

From `com.phonepe.sentinelai.evals.tests.Expectations`:

- `outputContains(String substring)`
- `outputEquals(R expected)`
- `jsonPathEquals(String jsonPath, Object expectedValue)`
- `toolCalled(String toolName)`
- `toolCalled(String toolName, int times)`
- `toolCalled(String toolName, int times, Map<String, Object> expectedParams)`
- `ordered(...)` for ordered message-level expectations

You can also implement custom expectations by implementing:

```java
Expectation<R, T>
```

and overriding:

```java
boolean evaluate(R result, EvalExpectationContext<T> context)
```

## Runtime configuration

`EvalRunConfig` supports three practical controls:

- `samplePercentage`: run a deterministic sample instead of all cases.
- `sampleSeed`: seed for reproducible sampling.
- `minimumSampleSize`: lower bound when sampling.
- `failFast`: stop after the first failed test case.
- `defaultTestCaseTimeout`: fallback timeout for test cases without explicit timeout.

Example:

```java
import com.phonepe.sentinelai.evals.EvalRunConfig;

final var config = EvalRunConfig.builder()
        .samplePercentage(30)
        .sampleSeed(7L)
        .minimumSampleSize(2)
        .failFast(false)
        .defaultTestCaseTimeout(Duration.ofSeconds(10))
        .build();

final var report = new EvalEngine().run(dataset, agent, config);
```

You can override timeout per test case:

```java
new TestCase("slow flow",
             List.of(Expectations.outputContains("ok")),
             Duration.ofSeconds(2));
```

## Understanding reports

`EvalReport` contains:

- total/sampled/executed test-case counts,
- passed/failed/skipped counts,
- total run duration,
- per-case `TestCaseReport` entries with expectation details.

Common CI gate checks:

- Require `failedTestCases == 0` for standard regression gates.
- Optionally require `skippedTestCases == 0` when timeout strictness matters.

## CI usage pattern

Typical workflow:

1. Create dataset(s) for smoke and regression suites.
2. Run a small sampled suite on pull requests.
3. Run a larger/full suite on main or release branches.
4. Fail the job when report thresholds are not met.

A simple gate in Java test code can assert:

```java
assertEquals(0, report.getFailedTestCases());
```

## References

- `sentinel-evals/src/main/java/com/phonepe/sentinelai/evals/EvalEngine.java`
- `sentinel-evals/src/main/java/com/phonepe/sentinelai/evals/EvalRunConfig.java`
- `sentinel-evals/src/main/java/com/phonepe/sentinelai/evals/tests/Expectations.java`
- `sentinel-evals/src/test/java/com/phonepe/sentinelai/evals/EvalEngineTest.java`
- `sentinel-evals/src/test/java/com/phonepe/sentinelai/evals/PartialOutputEvalTest.java`



