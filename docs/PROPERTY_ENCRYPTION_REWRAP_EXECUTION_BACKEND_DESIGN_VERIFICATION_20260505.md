# Property Encryption Rewrap Execution Backend Design Verification

Date: 2026-05-05

## Context

The previous rewrap slice added a durable planned-job ledger with dry-run, plan, list, and get endpoints. This slice turns that ledger into an executable backend workflow while keeping UI execution controls out of scope.

The implementation follows the existing property-encryption backfill job pattern: claim a planned job, run asynchronously through a bounded executor, update each JSONB candidate with compare-and-set semantics, and terminal-mark the ledger row.

## Design

### Admin API

Added admin-only endpoints under `/api/v1/admin/property-encryption`:

- `POST /rewrap-jobs/{jobId}/run`
- `POST /rewrap-jobs/{jobId}/cancel`

Run behavior:

- Claims `PLANNED -> RUNNING`.
- Starts async execution through `PropertyEncryptionRewrapRunner`.
- Returns the claimed `RUNNING` ledger row with `202 Accepted`.
- If the async executor rejects start, terminal-marks the job `FAILED` and returns `503`.

Cancel behavior:

- `PLANNED` jobs are immediately marked `CANCELLED`.
- `RUNNING` jobs are marked `CANCEL_REQUESTED` and the runner stops at a safe boundary.

### Execution Safety

The service enforces these preconditions before mutating encrypted node payloads:

- secret crypto must be enabled
- job target key version must be present
- job target key version must equal the active key version
- active key version must be configured
- every key version in the planned snapshot must still be configured
- planned missing-source-key list must be empty
- planned malformed/unversioned encrypted payload count must be zero
- planned values requiring rewrap must be greater than zero

The active-key equality check is intentional. `SecretCryptoService.protect(...)` always writes with the active key, so allowing target and active key to diverge would create a misleading ledger row.

### JSONB Candidate Flow

Added `NodeRepository` rewrap primitives:

- `findRewrapCandidatesByTargetKeyVersionAndDeletedFalse(targetKeyVersion, limit)`
- `rewrapEncryptedPropertyIfUnchanged(...)`

Candidate shape:

- `nodeId`
- `propertyKey`
- `encryptedValue`
- `entityVersion`

The query uses `jsonb_each_text` and `->>`-style string comparison. This is required because encrypted payloads are JSON string values and should be compared without surrounding JSON quotes.

Candidate update flow:

1. Parse source key version from `enc:{version}:{payload}`.
2. Reject missing source keys before decrypting.
3. Call `SecretCryptoService.reveal(encryptedValue)`.
4. Call `SecretCryptoService.protect(plaintextValue)`.
5. Assert the new payload starts with `enc:{targetKeyVersion}:`.
6. Update the node encrypted property with version and value compare-and-set.

This avoids the main crypto pitfall: calling `protect()` directly on an existing `enc:...` payload would be idempotent and leave the old key version unchanged.

### Ledger Transitions

Added `PropertyEncryptionRewrapJobRepository` state-transition helpers:

- claim planned job
- request cancel
- immediate planned-job cancel
- start-failure terminal mark
- terminal mark for running or cancel-requested jobs

Terminal counters:

- `processedValueCount`
- `rewrappedValueCount`
- `skippedValueCount`
- `failedValueCount`

Terminal states:

- `SUCCEEDED` when no remaining values require rewrap
- `FAILED` on candidate failure, malformed payloads, or remaining values after candidate exhaustion
- `CANCELLED` when cancel is requested before or during execution

## Verification

### Backend Targeted Suite

Command:

```bash
cd ecm-core
/tmp/apache-maven-3.9.9/bin/mvn -B -Dstyle.color=never \
  -Dmaven.repo.local=.m2-cache/repository \
  -Dtest=PropertyEncryptionOperationsServiceTest,PropertyEncryptionOperationsControllerSecurityTest,PropertyEncryptionBackfillPostgresIntegrationTest \
  test
```

Result:

```text
Tests run: 54, Failures: 0, Errors: 0, Skipped: 2
BUILD SUCCESS
```

Notes:

- `PropertyEncryptionOperationsControllerSecurityTest`: 14 tests passed.
- `PropertyEncryptionOperationsServiceTest`: 38 tests passed.
- `PropertyEncryptionBackfillPostgresIntegrationTest`: 2 tests skipped because Docker/Testcontainers is unavailable on this host.

New coverage:

- admin role protection for rewrap run/cancel
- async executor rejection maps to failed rewrap ledger row
- successful rewrap execution counts processed/rewrapped/skipped values
- rewrap execution fails safely if target key is no longer active
- cancel-requested jobs terminal-mark as cancelled before processing
- cancel endpoint reloads the updated ledger row
- PostgreSQL integration test for rewrap execution is present and compiles, but needs Docker to run

### Environment Constraint

The repository `./mvnw` delegates Maven execution to Docker. This host has no reachable Docker socket, so tests were run with a temporary Maven 3.9.9 binary under `/tmp` and the repo-local `.m2-cache/repository`.

Docker-gated PostgreSQL execution still needs to be run on CI or a Docker-capable workstation.

## Remaining Work

Remaining work to close the Property Encryption benchmark after this slice: about `2.5-4.5 person-days`, plus Docker issue buffer if the PostgreSQL gate exposes real failures.

Recommended order:

1. Run Docker-backed PostgreSQL property-encryption gate.
2. Add rewrap execution UI controls and mocked browser coverage.
3. Close runtime masking/redaction policy.
4. Update final acceptance matrix after the first Docker-backed green run.

Do not expose frontend run/cancel controls until the frontend consumes these backend endpoints and has mocked route coverage for failure/cancel states.
