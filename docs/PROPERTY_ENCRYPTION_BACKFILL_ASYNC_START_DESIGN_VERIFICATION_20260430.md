# Property Encryption Backfill Async Start Design and Verification

Date: 2026-04-30
Code commit: `eaca956`

## Context

The previous property-encryption operations slice exposed `/run`, but the endpoint executed the full backfill in the request thread. That was acceptable for proving the operation contract, but it was not safe as the default operator path for larger backfills.

This slice converts the admin run endpoint into an async start operation:

- The request thread atomically claims a `PLANNED` job as `RUNNING`.
- The controller returns `202 Accepted` with the claimed ledger row.
- A Spring `@Async` runner executes the already-claimed job outside the HTTP request.

This remains backend-only. No frontend UI, queue, scheduler, async task registry, or dedicated executor pool was added.

## Design

### API Behavior

Endpoint:

```text
POST /api/v1/admin/property-encryption/backfill-jobs/{jobId}/run
```

The optional request body is unchanged:

```json
{
  "batchSize": 100
}
```

Behavior changed from synchronous execution to async start:

```text
PLANNED --claimBackfillJobForExecution--> RUNNING --background runner--> terminal
```

The endpoint now returns:

```text
202 Accepted
```

with the claimed `RUNNING` job DTO. Operators can poll the existing job read endpoint for terminal state.

### Controller Boundary

`PropertyEncryptionOperationsController` now performs only request-safe work:

- Resolve the actor name from `Authentication`.
- Resolve the optional `batchSize`.
- Claim the job with `claimBackfillJobForExecution(jobId)`.
- Start `PropertyEncryptionBackfillRunner.runClaimedBackfillJob(...)`.
- Return the claimed row with `202 Accepted`.

The async runner receives the actor string, not the `Authentication` object or `SecurityContext`, so it does not depend on request-thread security state.

### Service Split

`PropertyEncryptionOperationsService` now has three execution entrypoints:

- `claimBackfillJobForExecution(...)`: atomic `PLANNED -> RUNNING` claim, used by the controller before returning `202`.
- `runClaimedBackfillJob(...)`: executes only jobs already in `RUNNING` or `CANCEL_REQUESTED`.
- `runBackfillJob(...)`: retained as a synchronous internal helper that claims and then executes for tests or internal callers.

This split keeps the concurrency guard in the ledger, not in an in-memory runner.

### Async Runner

`PropertyEncryptionBackfillRunner` is a separate Spring bean with an `@Async` method. This avoids the common self-invocation trap where an `@Async` method called from the same class would run synchronously.

The runner catches and logs background exceptions. The service still writes terminal state for normal execution failures; the catch protects the async executor from propagating uncaught exceptions to the caller after `202` has already been returned.

### Cancellation Edge

If a job is cancelled after it is claimed but before the background runner starts, `runClaimedBackfillJob(...)` accepts `CANCEL_REQUESTED` and terminal-marks the job as `CANCELLED` without processing candidates.

## Verification

### Static Checks

Commands:

```bash
git diff --check
perl -ne 'print "$ARGV:$.:$_" if /[^\x00-\x7F]/' ecm-core/src/main/java/com/ecm/core/controller/PropertyEncryptionOperationsController.java ecm-core/src/main/java/com/ecm/core/service/PropertyEncryptionOperationsService.java ecm-core/src/main/java/com/ecm/core/service/PropertyEncryptionBackfillRunner.java ecm-core/src/test/java/com/ecm/core/controller/PropertyEncryptionOperationsControllerSecurityTest.java ecm-core/src/test/java/com/ecm/core/service/PropertyEncryptionOperationsServiceTest.java ecm-core/src/test/java/com/ecm/core/service/PropertyEncryptionBackfillRunnerTest.java
```

Result: both passed with no output.

### Java Compile-Level Check

Local Maven was not available, so the changed Java files were compiled directly with Java 17 and the checked-in `.m2-cache` dependency cache.

Main files:

```bash
cd ecm-core
CP="target/classes:$(find .m2-cache/repository -name '*.jar' -type f | tr '\n' ':')"
LOMBOK="$(find .m2-cache/repository -name 'lombok-*.jar' -type f | head -n 1)"
javac -parameters -cp "$CP" -processorpath "$LOMBOK" -d /tmp/athena-async-main-classes src/main/java/com/ecm/core/service/PropertyEncryptionBackfillRunner.java src/main/java/com/ecm/core/service/PropertyEncryptionOperationsService.java src/main/java/com/ecm/core/controller/PropertyEncryptionOperationsController.java
```

Result: passed.

Test files:

```bash
cd ecm-core
CP="/tmp/athena-async-main-classes:target/classes:target/test-classes:$(find .m2-cache/repository -name '*.jar' -type f | tr '\n' ':')"
LOMBOK="$(find .m2-cache/repository -name 'lombok-*.jar' -type f | head -n 1)"
javac -parameters -cp "$CP" -processorpath "$LOMBOK" -d /tmp/athena-async-test-classes src/test/java/com/ecm/core/service/PropertyEncryptionBackfillRunnerTest.java src/test/java/com/ecm/core/service/PropertyEncryptionOperationsServiceTest.java src/test/java/com/ecm/core/controller/PropertyEncryptionOperationsControllerSecurityTest.java
```

Result: passed. The compiler emitted the existing unchecked-operation note for `PropertyEncryptionOperationsServiceTest`; no compile errors were produced.

### Maven/JUnit Status

Current environment limitation:

- `/tmp/codex-maven/apache-maven-3.9.11/bin/mvn` is not present.
- `mvn` is not installed on `PATH`.
- `ecm-core/mvnw` delegates to Docker.
- Docker is unavailable at `unix:///Users/chouhua/.docker/run/docker.sock`, including after escalated execution.

Because of that, current JUnit execution was blocked by environment, not by a failing test. The last committed pre-async baseline remains the earlier targeted Maven result from the previous run/cancel slice, but it does not verify this async-start commit.

### Test Coverage Added or Updated

- Controller security test now verifies admin `/run` returns `202 Accepted`, returns a `RUNNING` row, and invokes the async runner with the resolved actor and batch size.
- Service test now rejects unclaimed direct execution and covers `CANCEL_REQUESTED` jobs being marked `CANCELLED` before candidate processing.
- Runner test verifies delegation to `runClaimedBackfillJob(...)` and non-propagation of background execution failures.

## Review Notes

A parallel code review confirmed the selected Spring shape:

- Use a separate runner bean for `@Async`.
- Claim in the request thread before returning.
- Pass primitive request-derived values into the background thread.
- Avoid `CompletableFuture.runAsync` and the common pool.

## Non-Goals

- No dedicated async executor or queue.
- No scheduler.
- No per-job progress stream.
- No frontend page.
- No terminal-state recovery worker for the rare case where the async task dies after the claim but before terminal update.

## Remaining Work

Recommended next slices:

1. Add a named, bounded async executor for property-encryption operations instead of using the default Spring async executor.
2. Add a recovery or watchdog path for stale `RUNNING` jobs if a node dies mid-run.
3. Add Docker-backed integration coverage for plan -> async start -> poll terminal state when Docker is available.
4. Add frontend admin UI after the backend operation lifecycle is stable.
