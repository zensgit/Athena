# OpsRecoveryController Response-Contract Tests

Date: 2026-05-23

## Context

This slice continues the backend response-contract track after the
DocumentController version/checkout follow-up. The TODO ranks
OpsRecoveryController as a Tier 2 contract risk because the frontend
`opsRecoveryService` consumes a broad async-task surface with nullable
lifecycle fields.

## Scope

Added:

- `ecm-core/src/test/java/com/ecm/core/controller/OpsRecoveryControllerResponseContractTest.java`

Covered JSON endpoints:

- `POST /api/v1/ops/recovery/history/export-async`
- `GET /api/v1/ops/recovery/history/export-async/{taskId}`
- `GET /api/v1/ops/recovery/history/export-async`
- `GET /api/v1/ops/recovery/history/export-async/summary`

Out of scope:

- CSV export/download endpoints.
- Retry-terminal endpoints.
- Cleanup and cancel-active mutation response contracts.
- History, summary, compare, trend, dry-run, queue, clear, and replay
  endpoints outside the async export task lifecycle.
- Controller implementation changes.
- Frontend changes.

## Design

The test uses `@WebMvcTest(OpsRecoveryController.class)` with mocked
repositories/services and an admin user. It starts one async HISTORY export
task and deliberately blocks the mocked audit-log repository so the task
stabilizes in `RUNNING` state. That gives deterministic assertions for nullable
task fields without relying on the background task naturally completing.

The slice locks these wire DTOs:

- `RecoveryHistoryExportAsyncCreateResponseDto`
- `RecoveryHistoryExportAsyncStatusResponseDto`
- `RecoveryHistoryExportAsyncRequestSnapshotDto`
- `RecoveryHistoryExportAsyncListResponseDto`
- `RecoveryTaskCenterPagingDto`
- `RecoveryHistoryExportAsyncSummaryResponseDto`

The tests lock:

- create-response field order and deduplication fields;
- request snapshot numeric limits plus explicit JSON nulls for omitted filters;
- status-response nullable lifecycle fields (`error`, `finishedAt`,
  `filename`) as explicit JSON nulls while the task is running;
- `startedAt`, `createdAt`, `timeoutAt`, and `expiresAt` presence;
- list envelope fields (`count`, `paging`, `items`) and paging subfields;
- summary counter field set, including `timedOutCount` and `expiredCount`.

## Verification

Local static hygiene:

```bash
git diff --check -- . ':!.env'
```

Result: passed.

Targeted Maven test:

```bash
cd ecm-core
./mvnw -Dtest=OpsRecoveryControllerResponseContractTest test
```

Result: blocked by the local environment before Maven startup:

```text
failed to connect to the docker API at unix:///Users/chouhua/.docker/run/docker.sock; check if the path is correct and if the daemon is running: dial unix /Users/chouhua/.docker/run/docker.sock: connect: no such file or directory
```

Direct `mvn` is not installed in this environment:

```text
zsh:1: command not found: mvn
```

CI is the authoritative execution gate for this slice.

## CI Diagnostic Follow-Up

Initial CI run `26329262548` failed in Backend Verify. The failure was in the
new contract test, before any production code was exercised as broken:

```text
OpsRecoveryControllerResponseContractTest.historyExportAsyncEndpointsLockResponseContracts:156 expected: <true> but was: <false>
```

Root cause: the test request included `actor` and `eventType` filters, but the
mocked repository latch only covered the unfiltered HISTORY query path. The
background task correctly chose a different repository method, so the latch used
to stabilize the `RUNNING` status never fired.

Forward fix: keep the slice focused on the base async export contract by using
only `exportType`, `limit`, and `days`, and assert the omitted snapshot filters
as explicit JSON nulls.
