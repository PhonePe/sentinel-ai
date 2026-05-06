# sentinel-evals-junit5

JUnit 5 assertion helpers for `sentinel-evals`.

## Dependency

```xml
<dependency>
    <groupId>com.phonepe.sentinel-ai</groupId>
    <artifactId>sentinel-evals-junit5</artifactId>
    <scope>test</scope>
</dependency>
```

## Usage

```java
import com.phonepe.sentinelai.evals.EvalEngine;
import com.phonepe.sentinelai.evals.junit.assertions.EvalReportAssertions;

var report = new EvalEngine().run(dataset, agent);
EvalReportAssertions.assertNoFailures(report);
```

`assertNoFailures` renders detailed expectation-level diagnostics when evals fail.

