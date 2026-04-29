# Property Encryption Backfill Executor Design and Verification

Date: 2026-04-29
Code commit: `89e3422`

## Context

This slice follows the property-encryption dry-run, job-ledger, candidate-selection, and one-row CAS update slices. The goal was to add the first internal batch executor that can consume a planned backfill job and migrate plaintext values for encrypted property definitions into encrypted node storage.

This remains an internal service capability. No public controller, scheduler, cancel endpoint, or operator start endpoint was added in this slice.

## Design

### Execution Boundary

`PropertyEncryptionOperationsService.runBackfillJob(...)` is intentionally not annotated with a long outer transaction. The executor uses short repository/service transactions instead:

- `claimPlannedJob(...)` atomically changes `PLANNED -> RUNNING`.
- Each candidate update calls the existing one-row CAS path through `backfillEncryptedPropertyIfUnchanged(...)`.
- `markTerminalIfRunning(...)` atomically writes the terminal state only while the job is still `RUNNING`.

This avoids holding a transaction across a whole batch and prevents two workers from executing the same planned job.

### Claim and Terminal Guards

`PropertyEncryptionBackfillJobRepository` now exposes business-semantic defaults:

- `claimPlannedJob(jobId, startedAt)`
- `markTerminalIfRunning(...)`

Internally, both delegate to parameterized JPQL update methods. This matters because Hibernate did not accept the original nested enum literal form in JPQL. The repository test now validates the real query parsing and update behavior, not just service mocks.

### Preconditions

Before processing candidates, the executor verifies:

- Secret crypto is enabled.
- The job target key version is present.
- The job target key version still matches the active key version.
- The active key version is configured.
- No dual-storage conflicts exist for the planned encrypted definitions.

If any precondition fails, the claimed job is marked `FAILED` with `lastError` and zero candidate counters.

### Batch Processing

The executor iterates the job's definition snapshot and fetches bounded candidate batches by property qualified name. Candidate outcomes are counted as:

- `processed`: a unique node/property candidate was attempted.
- `migrated`: the one-row CAS update affected one row.
- `skipped`: the CAS update affected zero rows.
- `failed`: encryption or update threw an exception.

Repeated node/property candidates within one executor run are not reprocessed. If a batch only returns already attempted candidates, the loop breaks and terminal reconciliation decides whether the job can succeed.

### Terminal Reconciliation

A run only succeeds when:

- No candidate failed.
- Re-counted dual-storage conflicts are zero.
- Re-counted ready values are zero.

If ready values remain after candidate processing, the job fails with `Backfill job ended with remaining ready values`. This avoids silently succeeding when the planned snapshot and actual candidate query diverge.

## Verification

### Targeted Executor and Security Tests

Command:

```bash
/tmp/codex-maven/apache-maven-3.9.11/bin/mvn -q -Dmaven.repo.local=.m2-cache/repository -Dspring.profiles.active=test -Dtest=PropertyEncryptionBackfillJobRepositoryTest,PropertyEncryptionOperationsServiceTest,PropertyEncryptionOperationsControllerSecurityTest test
```

Result:

| Test class | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| `PropertyEncryptionBackfillJobRepositoryTest` | 1 | 0 | 0 | 0 |
| `PropertyEncryptionOperationsServiceTest` | 25 | 0 | 0 | 0 |
| `PropertyEncryptionOperationsControllerSecurityTest` | 8 | 0 | 0 | 0 |

### Service Test Rerun After Cleanup

Command:

```bash
/tmp/codex-maven/apache-maven-3.9.11/bin/mvn -q -Dmaven.repo.local=.m2-cache/repository -Dspring.profiles.active=test -Dtest=PropertyEncryptionOperationsServiceTest test
```

Result: `25 tests, 0 failures, 0 errors, 0 skipped`.

### JSONB Predicate Smoke

Command:

```bash
/tmp/codex-maven/apache-maven-3.9.11/bin/mvn -q -Dmaven.repo.local=.m2-cache/repository -Dspring.profiles.active=test -Dtest=NodeRepositoryJsonbBackfillSmokeTest,PropertyEncryptionOperationsServiceTest test
```

Result:

| Test class | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| `NodeRepositoryJsonbBackfillSmokeTest` | 1 | 0 | 0 | 1 |
| `PropertyEncryptionOperationsServiceTest` | 25 | 0 | 0 | 0 |

The smoke test skipped because the local machine did not expose a Docker socket at `/var/run/docker.sock`.

### Full Security Sweep

Command:

```bash
/tmp/codex-maven/apache-maven-3.9.11/bin/mvn -q -Dmaven.repo.local=.m2-cache/repository -Dspring.profiles.active=test '-Dtest=*SecurityTest' test
```

Result: `46 files, 422 tests, 0 failures, 0 errors, 0 skipped`.

### Static Checks

Commands:

```bash
git diff --check
perl -ne 'print "$ARGV:$.:$_" if /[^\x00-\x7F]/' ecm-core/src/main/java/com/ecm/core/repository/NodeRepository.java ecm-core/src/main/java/com/ecm/core/repository/PropertyEncryptionBackfillJobRepository.java ecm-core/src/main/java/com/ecm/core/service/PropertyEncryptionOperationsService.java ecm-core/src/test/java/com/ecm/core/service/PropertyEncryptionOperationsServiceTest.java ecm-core/src/test/java/com/ecm/core/repository/PropertyEncryptionBackfillJobRepositoryTest.java
```

Result: both passed with no output.

## Notes From Review

The repository test found a real issue before commit: hard-coded nested enum constants in JPQL failed query validation. The final implementation uses enum parameters instead.

The executor also includes a duplicate-candidate guard. Without it, a candidate query that repeatedly returns the same node/property could loop without making progress.

## Non-Goals

- No admin API to start execution.
- No scheduler.
- No cancel or pause workflow.
- No cursor, heartbeat, or per-node failure ledger.
- No UI.

## Remaining Work

Recommended next slices:

1. Add an internal or admin-gated start endpoint only after deciding the operator workflow.
2. Add cancel/request-cancel semantics before long scheduled runs.
3. Add Docker-backed integration coverage for JSONB predicate plus executor when Docker is available.
4. Consider a per-candidate failure ledger if operations need resumable partial failure analysis.
