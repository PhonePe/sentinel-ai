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

Add the dependency (version managed via the Sentinel BOM):

```xml
<dependency>
    <groupId>com.phonepe.sentinel-ai</groupId>
    <artifactId>sentinel-evals</artifactId>
</dependency>
```

Create a dataset, attach expectations, run it against your agent, and assert on the report:

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
assertEquals(0, report.getFailedTestCases());
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

Use `Expectations.answerRelevance(...)` for one-step wiring, or `Metrics.answerRelevance(...)` to get the raw metric.

| Expectation helper | Metric class | What it measures |
|---|---|---|
| `answerRelevance(judgeModel [, promptTemplate] [, threshold])` | `OutputRelevanceMetric` | LLM-judged relevance of the answer to the request |
| `Metrics.answerRelevance(judgeModel [, promptTemplate])` | `OutputRelevanceMetric` | Raw metric — wire your own threshold via `MetricExpectation` |

The default judge prompt evaluates three dimensions:

- **Intent coverage** — did the answer address what was asked?
- **Request scope alignment** — did the answer stay within the factual scope of the question?
- **No off-topic content** — no irrelevant filler or tangential information?

Custom judge prompts must contain `{request}` and `{answer}` placeholders, which are validated at construction time.

#### Writing a custom LLM-judged metric

Extend `AbstractLlmJudgeMetric<T>` to build your own judge metric (e.g. groundedness, toxicity, style). Only two things are needed: a judge prompt and a score parser.

```java
public class AnswerGroundednessMetric<T> extends AbstractLlmJudgeMetric<T> {
    private static final String TEMPLATE = """
            Does the answer contain only facts grounded in the provided context?
            Return: {"score": 0.0-1.0, "reason": "..."}
            Context: {request}
            Answer: {answer}
            """;

    public AnswerGroundednessMetric(Model judgeModel) {
        super(judgeModel, TEMPLATE, List.of("{request}", "{answer}"));
    }

    @Override
    public String metricName() { return "AnswerGroundedness"; }

    @Override
    protected double parseScore(ModelOutput output) {
        final var node = readModelOutputPayload(output);
        if (node == null || !node.has("score")) return 0.0;
        final var score = node.get("score").doubleValue();
        return (score >= 0.0 && score <= 1.0) ? score : 0.0;
    }

    @Override
    protected String renderPrompt(String request, String answer) {
        return promptTemplate().replace("{request}", request).replace("{answer}", answer);
    }
}
```

Then use it via `MetricExpectation`:

```java
var expectation = new MetricExpectation<>(new AnswerGroundednessMetric<>(judgeModel), 0.85);
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

Wrap the run inside a JUnit test and assert on the report. The test will fail the build when any eval fails.

```java
@Test
void agentSmokeEvals() {
    var report = new EvalEngine().run(dataset, agent,
        EvalRunConfig.builder()
            .samplePercentage(30)   // run 30 % of cases on PRs
            .failFast(true)
            .defaultTestCaseTimeout(Duration.ofSeconds(15))
            .build());

    assertEquals(0, report.getFailedTestCases(),
        "Eval failures:\n" + report.getTestCaseReports().stream()
            .filter(r -> r.getStatus() == EvalStatus.FAILED)
            .map(r -> r.getInput() + " → " + r.getDetails())
            .collect(Collectors.joining("\n")));
}
```

Use `samplePercentage` to keep PR builds fast while running the full suite on merges to main.

