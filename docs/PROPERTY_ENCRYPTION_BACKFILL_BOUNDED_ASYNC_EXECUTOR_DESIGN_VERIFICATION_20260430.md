# Property Encryption Backfill Bounded Async Executor Design and Verification

Date: 2026-04-30
Code commit: `3677a65`

## Context

The previous slice converted property-encryption backfill `/run` into an async start operation, but the runner still used Spring's default async executor. That made executor capacity implicit and mixed this operationally sensitive backfill lane with unrelated `@Async` work.

This slice binds property-encryption backfill execution to a named, bounded executor.

## Design

### Dedicated Executor

New bean:

```text
propertyEncryptionBackfillTaskExecutor
```

Configuration:

| Setting | Value |
| --- | ---: |
| Core pool size | 1 |
| Max pool size | 2 |
| Queue capacity | 20 |
| Thread prefix | `prop-enc-backfill-` |
| Shutdown wait | enabled |
| Await termination | 30 seconds |
| Rejection handler | `ThreadPoolExecutor.CallerRunsPolicy` |

The conservative pool keeps property-encryption backfill throughput bounded. This matters because each run can perform database reads, JSONB predicate work, encryption, and CAS updates.

### Runner Binding

`PropertyEncryptionBackfillRunner.runClaimedBackfillJob(...)` now uses:

```java
@Async(PropertyEncryptionAsyncConfiguration.PROPERTY_ENCRYPTION_BACKFILL_TASK_EXECUTOR)
```

The controller path remains unchanged: it claims the job in the request thread, invokes the runner bean, and returns `202 Accepted` with the claimed `RUNNING` row.

### Backpressure Choice

The executor uses `CallerRunsPolicy`. This is deliberate for this intermediate slice:

- It avoids silently dropping a claimed job.
- It avoids a rejected async submission leaving a job stuck in `RUNNING`.
- Under saturation, the request thread may perform the work instead of returning quickly.

The last point is a known tradeoff. Strict "never run in request thread" semantics should be handled in a later slice by catching `TaskRejectedException` and terminal-marking the claimed job as start-failed, or by moving execution into a durable queue/worker.

## Tests Added

`PropertyEncryptionAsyncConfigurationTest`

- Verifies pool size, queue capacity, thread prefix, and rejection handler.

`PropertyEncryptionBackfillRunnerTest`

- Verifies the runner method is annotated with the named executor.
- Retains delegate and exception-swallowing coverage.

`PropertyEncryptionBackfillRunnerAsyncProxyTest`

- Starts a minimal Spring context with `@EnableAsync`.
- Verifies the executor bean exists.
- Verifies the runner bean is AOP-proxied, covering the Spring proxy path that direct Mockito construction cannot test.

## Verification

### Static Checks

Commands:

```bash
git diff --check
perl -ne 'print "$ARGV:$.:$_" if /[^\x00-\x7F]/' ecm-core/src/main/java/com/ecm/core/config/PropertyEncryptionAsyncConfiguration.java ecm-core/src/main/java/com/ecm/core/service/PropertyEncryptionBackfillRunner.java ecm-core/src/test/java/com/ecm/core/config/PropertyEncryptionAsyncConfigurationTest.java ecm-core/src/test/java/com/ecm/core/service/PropertyEncryptionBackfillRunnerTest.java ecm-core/src/test/java/com/ecm/core/service/PropertyEncryptionBackfillRunnerAsyncProxyTest.java
```

Result: both passed with no output.

### Java Compile-Level Check

Main files:

```bash
cd ecm-core
CP="target/classes:$(find .m2-cache/repository -name '*.jar' -type f | tr '\n' ':')"
LOMBOK="$(find .m2-cache/repository -name 'lombok-*.jar' -type f | head -n 1)"
javac -parameters -cp "$CP" -processorpath "$LOMBOK" -d /tmp/athena-executor-main-classes src/main/java/com/ecm/core/config/PropertyEncryptionAsyncConfiguration.java src/main/java/com/ecm/core/service/PropertyEncryptionOperationsService.java src/main/java/com/ecm/core/service/PropertyEncryptionBackfillRunner.java
```

Result: passed.

Test files:

```bash
cd ecm-core
CP="/tmp/athena-executor-main-classes:target/classes:target/test-classes:$(find .m2-cache/repository -name '*.jar' -type f | tr '\n' ':')"
LOMBOK="$(find .m2-cache/repository -name 'lombok-*.jar' -type f | head -n 1)"
javac -parameters -cp "$CP" -processorpath "$LOMBOK" -d /tmp/athena-executor-test-classes src/test/java/com/ecm/core/config/PropertyEncryptionAsyncConfigurationTest.java src/test/java/com/ecm/core/service/PropertyEncryptionBackfillRunnerTest.java src/test/java/com/ecm/core/service/PropertyEncryptionBackfillRunnerAsyncProxyTest.java
```

Result: passed.

### Maven/JUnit Status

Targeted command attempted:

```bash
./mvnw -q -Dmaven.repo.local=.m2-cache/repository -Dspring.profiles.active=test -Dtest=PropertyEncryptionAsyncConfigurationTest,PropertyEncryptionBackfillRunnerTest,PropertyEncryptionBackfillRunnerAsyncProxyTest test
```

Result: blocked by local Docker availability:

```text
failed to connect to the docker API at unix:///Users/chouhua/.docker/run/docker.sock
```

The same command was retried with escalated execution and failed with the same Docker socket error. `junit-platform-console-standalone` was not present in `.m2-cache`, so there was no local non-Maven JUnit runner to use.

## Parallel Review

A parallel read-only review checked existing async configuration and confirmed:

- `@EnableAsync` already exists in `EcmCoreApplication`.
- There was no existing `AsyncConfigurer` or dedicated property-encryption executor before this slice.
- The new `PropertyEncryptionAsyncConfiguration` location and bean name are reasonable.
- A Spring context proxy smoke test is useful because direct runner instantiation does not prove `@Async` proxy behavior.

## Non-Goals

- No durable queue.
- No async task registry integration.
- No stale `RUNNING` recovery.
- No frontend change.
- No strict rejection-to-terminal-failure workflow.

## Remaining Work

Recommended next slices:

1. Add rejected-start handling if strict fast-return semantics are required under saturation.
2. Add stale `RUNNING` recovery or watchdog behavior for node crashes during backfill.
3. Add Docker-backed JUnit/integration verification when Docker is available.
4. Add frontend admin UI only after the backend operation lifecycle is finalized.
