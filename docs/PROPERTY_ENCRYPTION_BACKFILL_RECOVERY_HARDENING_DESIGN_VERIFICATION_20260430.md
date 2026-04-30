# Property Encryption Backfill Recovery Hardening Design and Verification

Date: 2026-04-30
Code commit: `7434b98`

## Context

The previous property-encryption backfill slice introduced a named bounded async executor. Its remaining operational risk was that `CallerRunsPolicy` could make `POST /backfill-jobs/{id}/run` execute the backfill on the request thread under executor saturation, and a node crash after claiming a job could leave the ledger stuck in an active state.

This slice hardens both edges:

- Async executor saturation now fails fast instead of running on the caller thread.
- Claimed jobs that cannot be submitted are terminal-marked `FAILED`.
- Stale active jobs are recovered by a scheduled watchdog.

## Design

### Strict async start

`PropertyEncryptionAsyncConfiguration` now uses:

```java
new ThreadPoolExecutor.AbortPolicy()
```

instead of `CallerRunsPolicy`. This preserves the async-start contract: a saturated executor will not silently run property-encryption backfill work in the HTTP request thread.

`PropertyEncryptionOperationsController` catches `TaskRejectedException` from the async proxy. If submission is rejected after the job has already been claimed, the controller calls:

```text
markBackfillJobStartFailed(jobId, lastError)
```

and returns:

```text
503 Service Unavailable
```

with the terminal `FAILED` job DTO. This avoids leaving an already-claimed job in `RUNNING` with no worker.

### Stale active-job recovery

`PropertyEncryptionBackfillRecoveryScheduler` runs on a configurable fixed delay:

| Property | Default |
| --- | ---: |
| `ecm.property-encryption.backfill.recovery.enabled` | `true` |
| `ecm.property-encryption.backfill.recovery.stale-after-minutes` | `360` |
| `ecm.property-encryption.backfill.recovery.fixed-delay-ms` | `300000` |
| `ecm.property-encryption.backfill.recovery.initial-delay-ms` | `300000` |

The service delegates to one bounded repository update:

- stale `RUNNING` jobs become `FAILED` with recovery `lastError`.
- stale `CANCEL_REQUESTED` jobs become `CANCELLED`.
- fresh active jobs are untouched.

The watchdog uses `startedAt < now - staleAfter`, so jobs without a start timestamp are not recovered by this path.

## Verification

### Static checks

Commands:

```bash
git diff --check
perl -ne 'print "$ARGV:$.:$_" if /[^\x00-\x7F]/' ecm-core/src/main/java/com/ecm/core/config/PropertyEncryptionAsyncConfiguration.java ecm-core/src/main/java/com/ecm/core/controller/PropertyEncryptionOperationsController.java ecm-core/src/main/java/com/ecm/core/repository/PropertyEncryptionBackfillJobRepository.java ecm-core/src/main/java/com/ecm/core/service/PropertyEncryptionOperationsService.java ecm-core/src/main/java/com/ecm/core/service/PropertyEncryptionBackfillRecoveryScheduler.java ecm-core/src/test/java/com/ecm/core/config/PropertyEncryptionAsyncConfigurationTest.java ecm-core/src/test/java/com/ecm/core/controller/PropertyEncryptionOperationsControllerSecurityTest.java ecm-core/src/test/java/com/ecm/core/repository/PropertyEncryptionBackfillJobRepositoryTest.java ecm-core/src/test/java/com/ecm/core/service/PropertyEncryptionOperationsServiceTest.java ecm-core/src/test/java/com/ecm/core/service/PropertyEncryptionBackfillRecoverySchedulerTest.java
```

Result: both passed with no output.

### Java compile-level check

Main files:

```bash
cd ecm-core
CP="target/classes:$(find .m2-cache/repository -name '*.jar' -type f | tr '\n' ':')"
LOMBOK="$(find .m2-cache/repository -name 'lombok-*.jar' -type f | head -n 1)"
javac -parameters -cp "$CP" -processorpath "$LOMBOK" -d /tmp/athena-recovery-main-classes src/main/java/com/ecm/core/config/PropertyEncryptionAsyncConfiguration.java src/main/java/com/ecm/core/repository/PropertyEncryptionBackfillJobRepository.java src/main/java/com/ecm/core/service/PropertyEncryptionOperationsService.java src/main/java/com/ecm/core/service/PropertyEncryptionBackfillRecoveryScheduler.java src/main/java/com/ecm/core/service/PropertyEncryptionBackfillRunner.java src/main/java/com/ecm/core/controller/PropertyEncryptionOperationsController.java
```

Result: passed.

Test files:

```bash
cd ecm-core
CP="/tmp/athena-recovery-main-classes:target/classes:target/test-classes:$(find .m2-cache/repository -name '*.jar' -type f | tr '\n' ':')"
LOMBOK="$(find .m2-cache/repository -name 'lombok-*.jar' -type f | head -n 1)"
javac -parameters -cp "$CP" -processorpath "$LOMBOK" -d /tmp/athena-recovery-test-classes src/test/java/com/ecm/core/config/PropertyEncryptionAsyncConfigurationTest.java src/test/java/com/ecm/core/controller/PropertyEncryptionOperationsControllerSecurityTest.java src/test/java/com/ecm/core/repository/PropertyEncryptionBackfillJobRepositoryTest.java src/test/java/com/ecm/core/service/PropertyEncryptionOperationsServiceTest.java src/test/java/com/ecm/core/service/PropertyEncryptionBackfillRecoverySchedulerTest.java src/test/java/com/ecm/core/service/PropertyEncryptionBackfillRunnerTest.java src/test/java/com/ecm/core/service/PropertyEncryptionBackfillRunnerAsyncProxyTest.java
```

Result: passed. The compiler emitted the existing unchecked-operation note for `PropertyEncryptionOperationsServiceTest`; no compile errors were produced.

### Maven/JUnit status

Targeted command attempted:

```bash
./mvnw -q -Dmaven.repo.local=.m2-cache/repository -Dspring.profiles.active=test -Dtest=PropertyEncryptionAsyncConfigurationTest,PropertyEncryptionBackfillRunnerTest,PropertyEncryptionBackfillRunnerAsyncProxyTest,PropertyEncryptionBackfillRecoverySchedulerTest,PropertyEncryptionOperationsServiceTest,PropertyEncryptionOperationsControllerSecurityTest,PropertyEncryptionBackfillJobRepositoryTest test
```

Result: blocked by local Docker availability:

```text
failed to connect to the docker API at unix:///Users/chouhua/.docker/run/docker.sock
```

The same command was retried with escalated execution and failed with the same Docker socket error.

## Test Coverage Added or Updated

- Executor config test now asserts `AbortPolicy`.
- Controller security test covers executor rejection returning `503` and terminal `FAILED` DTO.
- Repository test covers start-failure terminal update, stale `RUNNING -> FAILED`, stale `CANCEL_REQUESTED -> CANCELLED`, and fresh `RUNNING` preservation.
- Service test covers start-failure terminal marking and recovery delegation.
- Scheduler test covers disabled recovery and configured-threshold recovery.

## Non-Goals

- No durable queue or async task registry.
- No frontend change.
- No per-candidate failure ledger.
- No migration; the existing ledger fields are sufficient.

## Remaining Work

Recommended next slices:

1. Run Docker/PostgreSQL integration verification for migration + JSONB predicate + plan -> async run -> terminal poll.
2. Add property-encryption admin UI after backend recovery behavior has real-stack evidence.
3. Add rewrap execution only if key-rotation operations are in the current product target.
