# Property Encryption Backfill Run and Cancel Design and Verification

Date: 2026-04-29
Code commit: `31fc264`

## Context

The previous slice added the internal property-encryption backfill executor but deliberately did not expose an operator API. This slice adds the first admin-gated operation surface:

- Run a planned backfill job.
- Cancel a planned job or request cancellation for a running job.
- Make the executor honor cancellation before and during candidate processing.

This is still backend-only. No scheduler, UI, per-candidate failure ledger, or async task registry was added.

## API Surface

All endpoints remain under the existing admin-only controller:

```text
POST /api/v1/admin/property-encryption/backfill-jobs/{jobId}/run
POST /api/v1/admin/property-encryption/backfill-jobs/{jobId}/cancel
```

`/run` accepts an optional JSON body:

```json
{
  "batchSize": 100
}
```

The endpoint calls the existing internal executor and returns the refreshed job DTO. It is synchronous in this slice: processing happens in the request thread. The batch size remains clamped by the service to protect the repository query size, but this is not yet a background worker.

## State Machine

### Run

```text
PLANNED -> RUNNING -> SUCCEEDED
PLANNED -> RUNNING -> FAILED
PLANNED -> RUNNING -> CANCELLED
```

The claim is still atomic through `claimPlannedJob(...)`. A job that is no longer `PLANNED` cannot be claimed by a second runner.

### Cancel

```text
PLANNED -> CANCELLED
RUNNING -> CANCEL_REQUESTED -> CANCELLED
```

Cancel is intentionally idempotent from the caller perspective. If the repository update affects no row because the job already reached a terminal state, the service reloads and returns the current ledger row.

### Terminal Guard

The executor now writes terminal state through `markTerminalIfRunningOrCancelRequested(...)`. This allows a running executor to finish cleanly after another request has changed the ledger row to `CANCEL_REQUESTED`.

If cancellation arrives after candidate processing but before terminal update, cancellation wins when no candidate failure occurred. This avoids the race where a user requests cancellation but the executor writes `SUCCEEDED` milliseconds later.

## Repository Design

The repository continues to avoid JPQL enum literals. All status transitions use enum parameters because a previous repository test proved nested enum literals are not accepted by Hibernate query validation.

New repository operations:

- `existsByIdAndStatus(...)`
- `requestBackfillJobCancel(...)`
- `markTerminalIfRunningOrCancelRequested(...)`

The concrete update methods are guarded by expected status, so stale requests do not overwrite newer state.

## Executor Cancellation

The executor polls for `CANCEL_REQUESTED`:

- Before each definition.
- Before each candidate batch.
- Before each candidate update.

When cancellation is observed, the executor returns current counters and marks the job `CANCELLED`. No plaintext values are emitted or logged.

## Verification

### Targeted Backfill Operation Tests

Command:

```bash
/tmp/codex-maven/apache-maven-3.9.11/bin/mvn -q -Dmaven.repo.local=.m2-cache/repository -Dspring.profiles.active=test -Dtest=PropertyEncryptionBackfillJobRepositoryTest,PropertyEncryptionOperationsServiceTest,PropertyEncryptionOperationsControllerSecurityTest test
```

Result:

| Test class | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| `PropertyEncryptionBackfillJobRepositoryTest` | 2 | 0 | 0 | 0 |
| `PropertyEncryptionOperationsServiceTest` | 27 | 0 | 0 | 0 |
| `PropertyEncryptionOperationsControllerSecurityTest` | 9 | 0 | 0 | 0 |

### JSONB Backfill Predicate Smoke

Command:

```bash
/tmp/codex-maven/apache-maven-3.9.11/bin/mvn -q -Dmaven.repo.local=.m2-cache/repository -Dspring.profiles.active=test -Dtest=NodeRepositoryJsonbBackfillSmokeTest,PropertyEncryptionOperationsServiceTest test
```

Result:

| Test class | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| `NodeRepositoryJsonbBackfillSmokeTest` | 1 | 0 | 0 | 1 |
| `PropertyEncryptionOperationsServiceTest` | 27 | 0 | 0 | 0 |

The smoke test skipped because the local machine did not expose a Docker socket at `/var/run/docker.sock`.

### Full Security Sweep

Command:

```bash
/tmp/codex-maven/apache-maven-3.9.11/bin/mvn -q -Dmaven.repo.local=.m2-cache/repository -Dspring.profiles.active=test '-Dtest=*SecurityTest' test
```

Result: `46 files, 423 tests, 0 failures, 0 errors, 0 skipped`.

### Static Checks

Commands:

```bash
git diff --check
perl -ne 'print "$ARGV:$.:$_" if /[^\x00-\x7F]/' ecm-core/src/main/java/com/ecm/core/controller/PropertyEncryptionOperationsController.java ecm-core/src/main/java/com/ecm/core/repository/PropertyEncryptionBackfillJobRepository.java ecm-core/src/main/java/com/ecm/core/service/PropertyEncryptionOperationsService.java ecm-core/src/test/java/com/ecm/core/controller/PropertyEncryptionOperationsControllerSecurityTest.java ecm-core/src/test/java/com/ecm/core/repository/PropertyEncryptionBackfillJobRepositoryTest.java ecm-core/src/test/java/com/ecm/core/service/PropertyEncryptionOperationsServiceTest.java
```

Result: both passed with no output.

## Non-Goals

- No frontend UI.
- No scheduler.
- No async task registry or queue-backed background worker.
- No cancel reason field.
- No per-candidate failure ledger.

## Remaining Work

Recommended next slices:

1. Convert `/run` into an async operation or add a scheduler/worker path before using it for very large backfills.
2. Add Docker-backed integration coverage for a full dry-run -> plan -> run lifecycle when Docker is available.
3. Add a per-candidate failure ledger if operations need resumable failure analysis.
4. Add frontend admin UI only after the backend operation lifecycle is final.
