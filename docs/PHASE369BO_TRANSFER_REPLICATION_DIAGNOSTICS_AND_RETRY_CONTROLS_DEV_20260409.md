# Phase 369BO: Transfer Replication Diagnostics And Retry Controls

## Goal

Make outbound replication jobs diagnosable and operator-retryable instead of leaving failed transport runs as one-shot opaque failures.

## Scope

- Add replication job diagnostics fields for transport status, transport message, retry lineage, and last attempted timestamp
- Add manual retry control for failed or canceled replication jobs
- Keep transfer transport behavior unchanged; this phase is about job state and operator control
- Add focused backend tests and phase documentation

## Design

### Job diagnostics

- `ReplicationJob` now stores:
  - `retryOfJobId`
  - `attemptNumber`
  - `transportStatus`
  - `transportMessage`
  - `lastAttemptedAt`
- Diagnostics are updated in the same execution path that already transitions `PENDING -> RUNNING -> COMPLETED/FAILED`

### Retry control

- `POST /api/v1/replication/jobs/{jobId}/retry` creates a new queued job
- Retry is allowed only for `FAILED` and `CANCELED` jobs
- Retried jobs keep lineage via `retryOfJobId` and increment `attemptNumber`
- Original job history remains intact; retries do not mutate the original record into a new run

### Status semantics

- `status` remains the coarse lifecycle:
  - `PENDING`
  - `RUNNING`
  - `COMPLETED`
  - `FAILED`
  - `CANCELED`
- `transportStatus` adds transport-specific execution semantics:
  - `NEVER_RUN`
  - `RUNNING`
  - `SUCCESS`
  - `FAILED`

## Files

- `ecm-core/src/main/java/com/ecm/core/entity/ReplicationJob.java`
- `ecm-core/src/main/java/com/ecm/core/service/TransferReplicationService.java`
- `ecm-core/src/main/java/com/ecm/core/controller/TransferReplicationController.java`
- `ecm-core/src/main/resources/db/changelog/changes/063-add-replication-job-diagnostics-columns.xml`
- `ecm-core/src/test/java/com/ecm/core/service/TransferReplicationServiceTest.java`
- `ecm-core/src/test/java/com/ecm/core/controller/TransferReplicationControllerTest.java`

## Out of scope

- Automatic retry scheduling/backoff policy
- Per-target retry limits or circuit breaking
- Frontend retry/diagnostics operator surface
- Transport-level structured tracing
