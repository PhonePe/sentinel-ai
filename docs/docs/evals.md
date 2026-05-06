---
title: Evals
description: Evaluate Sentinel AI agents with datasets, expectations, and reports
---

# Evals

Evals are structured tests for AI agents — you define a set of inputs, describe what good output looks like, and run them repeatedly to catch regressions before they reach production.
Unlike unit tests, evals account for the non-deterministic nature of LLMs: they measure behavior across a range of inputs, track quality over time, and can use other models as judges when rule-based checks are not enough.

`sentinel-evals` is the built-in eval framework for Sentinel AI agents. It is fully generic (`Agent<R, T, A>`), works for both string and structured outputs, and integrates into any CI pipeline with a single assertion.

## Why use evals?

Agents are non-deterministic. Without evals you won't know when a prompt change silently breaks a tool-calling flow, when a model upgrade regresses output quality, or whether your agent handles edge-case inputs correctly before they reach production.
Evals give you a repeatable, versioned safety net — run them on every pull request as a gate, or on demand against live models for quality benchmarking.

## Getting started

Add dependency (version managed via the Sentinel BOM):

```xml
<dependency>
    <groupId>com.phonepe.sentinel-ai</groupId>
    <artifactId>sentinel-evals</artifactId>
</dependency>
```

Create a dataset, attach expectations, and run it against your agent to generate a report:

```java
import com.phonepe.sentinelai.evals.EvalEngine;
import com.phonepe.sentinelai.evals.tests.Dataset;
import com.phonepe.sentinelai.evals.tests.Expectations;
import com.phonepe.sentinelai.evals.tests.TestCase;

var dataset = new Dataset<>("smoke",
    List.of(
        new TestCase<>("What is the status?",
            List.of(
                Expectations.outputContains("OK"),
                Expectations.toolCalled("fetch_status"),
                Expectations.jsonPathEquals("$.status", "OK")
            ))));

var report = new EvalEngine().run(dataset, agent);
System.out.printf("Passed=%d Failed=%d Skipped=%d%n",
    report.getPassedTestCases(), report.getFailedTestCases(), report.getSkippedTestCases());
```

## Available evals

### Deterministic expectations

Deterministic expectations evaluate the agent output without any external model call. They are fast, free, and should be the first layer of every eval suite.

All helpers are available via `com.phonepe.sentinelai.evals.tests.Expectations`.

#### Output content

| Expectation | What it checks |
|---|---|
| `outputContains(String substring)` | Output string contains the given substring |
| `outputEquals(R expected)` | Output is exactly equal to the expected value |

#### JSON Path

Evaluates structured output (POJOs or JSON) using [JSONPath](https://github.com/json-path/JsonPath) expressions. Available operators: `EQ`, `NE`, `GT`, `GTE`, `LT`, `LTE`, `IN`, `NOT_IN`.

| Expectation | What it checks |
|---|---|
| `jsonPathEquals(String path, Object value)` | Shorthand for `where(path).eq(value)` |
| `where(String path).eq(value)` | Field at path equals value |
| `where(String path).ne(value)` | Field at path does not equal value |
| `where(String path).gt(value)` | Field at path is greater than value |
| `where(String path).gte(value)` | Field at path is greater than or equal to value |
| `where(String path).lt(value)` | Field at path is less than value |
| `where(String path).lte(value)` | Field at path is less than or equal to value |
| `where(String path).in(Collection)` | Field at path is contained in the set |
| `where(String path).notIn(Collection)` | Field at path is not contained in the set |
| `at(String path)` | Alias for `where(path)` |

#### Tool call assertions

Asserts that the agent called a specific tool during the run — useful for verifying agentic routing behavior.

| Expectation | What it checks |
|---|---|
| `toolCalled(String toolName)` | Tool was called at least once |
| `toolCalled(String toolName, int times)` | Tool was called exactly N times |
| `toolCalled(String toolName, int times, Map<String,Object> params)` | Tool was called N times with matching parameters |
| `ordered(MessageExpectation... expectations)` | Message-level expectations are satisfied in order |

---

### Embedding-based metrics

Embedding-based metrics use vector similarity to evaluate output quality without a live model call at evaluation time — they require an `EmbeddingModel` but are otherwise deterministic for a fixed model.

Use `Expectations.*` for one-step expectation wiring, or `Metrics.*` to get a raw `Metric<String, T>` to compose with a custom threshold via `MetricExpectation`.

| Expectation helper | Metric class | What it measures |
|---|---|---|
| `outputSimilarity(embeddingModel, referenceText [, threshold])` | `OutputSimilarityMetric` | Cosine similarity between output and a fixed reference text |
| `Metrics.outputSimilarity(embeddingModel, referenceText)` | `OutputSimilarityMetric` | Raw metric — wire your own threshold via `MetricExpectation` |
| `Metrics.outputRelevanceBySimilarity(embeddingModel)` | `OutputRelevanceBySimilarityMetric` | Cosine similarity between output and the original request — a proxy for topical relevance |

**When to use:** output similarity is useful when you have a reference or golden answer and want to catch semantic regressions without an exact-match assertion. `outputRelevanceBySimilarity` is useful when you only want to ensure the output stays on-topic with the original request.

**Limitation:** both are proximity measures — they cannot reason about intent, constraints, or factual accuracy. Use LLM-judged metrics below for that.

---

### LLM-judged metrics

LLM-judged metrics delegate scoring to a judge model that receives the original request and the agent answer, then returns a structured `{"score": 0.0–1.0, "reason": "..."}` payload. They are the most capable evaluators but require a live model call per test case.

Use `Expectations.answerRelevance(...)` for one-step wiring, or `Metrics.answerRelevance(...)` to get the raw metric. The judge model is injected through `DefaultMetricExecutorFactory`.

| Expectation helper | Metric class | What it measures |
|---|---|---|
| `answerRelevance([promptTemplate] [, threshold])` | `OutputRelevanceMetric` | LLM-judged relevance of the answer to the request |
| `Metrics.answerRelevance([promptTemplate])` | `OutputRelevanceMetric` | Raw metric — wire your own threshold via `MetricExpectation` |

The default judge prompt evaluates three dimensions:

- **Intent coverage** — did the answer address what was asked?
- **Request scope alignment** — did the answer stay within the factual scope of the question?
- **No off-topic content** — no irrelevant filler or tangential information?

Custom judge prompts must contain `{request}` and `{answer}` placeholders, which are validated at construction time.

```java
Model judgeModel = ...;

var expectation = Expectations.answerRelevance(0.85);

var metricExecutorFactory = new DefaultMetricExecutorFactory(judgeModel);
var expectationExecutorFactory = new DefaultExpectationExecutorFactory(metricExecutorFactory);
var evalEngine = new EvalEngine(mapper, expectationExecutorFactory);
```

---

## Running evals

### On demand

Run directly from a test class or a `main` method. No extra setup is required.

```java
var report = new EvalEngine().run(dataset, agent);
System.out.printf("Passed=%d Failed=%d Skipped=%d%n",
    report.getPassedTestCases(), report.getFailedTestCases(), report.getSkippedTestCases());
```

### In CI

Wrap the run inside a JUnit test and inspect the generated report. The test can fail the build when eval failures are detected.

```java
@Test
void agentSmokeEvals() {
    var report = new EvalEngine().run(dataset, agent,
        EvalRunConfig.builder()
            .samplePercentage(30)   // run 30 % of cases on PRs
            .failFast(true)
            .defaultTestCaseTimeout(Duration.ofSeconds(15))
            .build());

    System.out.printf("Passed=%d Failed=%d Skipped=%d%n",
        report.getPassedTestCases(), report.getFailedTestCases(), report.getSkippedTestCases());
}
```

Use `samplePercentage` to keep PR builds fast while running the full suite on merges to main.

## JUnit 5 integration (optional)

If you want rich assertion diagnostics in JUnit 5 tests, add the helper module:

```xml
<dependency>
    <groupId>com.phonepe.sentinel-ai</groupId>
    <artifactId>sentinel-evals-junit5</artifactId>
    <scope>test</scope>
</dependency>
```

Use `EvalReportAssertions` to fail with expectation-level diagnostics:

```java
import com.phonepe.sentinelai.evals.junit.assertions.EvalReportAssertions;

@Test
void agentSmokeEvals() {
    var report = new EvalEngine().run(dataset, agent);
    EvalReportAssertions.assertNoFailures(report);
}
```

## Real agent integration example

For end-to-end validation with a live model (no model mocking), see
`sentinel-evals/src/test/java/com/phonepe/sentinelai/evals/integration/RealNicknameAgentExpectationsIntegrationTest.java`.

This test demonstrates:

- typed request object (`name`, `age`) and structured nickname output
- deterministic expectations (`outputEquals`, `jsonPathEquals`, `where/at` operators)
- tool-call expectations (`toolCalled`, `ordered`)
- metric expectations (`outputSimilarity`, `answerRelevance`)

The test is gated to run only when real endpoints are enabled (for example with `-Preal-tests`).
