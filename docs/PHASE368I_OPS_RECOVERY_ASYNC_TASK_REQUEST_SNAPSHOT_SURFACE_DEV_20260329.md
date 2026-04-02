# PHASE368I Ops Recovery Async Task Request Snapshot Surface

## Goal

Continue the `OpsRecovery` operator-surface convergence by exposing the async history export request snapshot that Athena already keeps internally, but previously hid from create/status/list responses.

This phase makes async export tasks self-describing:

- operators can see what a task is exporting
- deduplicated/retried tasks preserve visible scope
- the task center no longer requires guessing from `taskId + exportType + filename`

## Why This Phase

Before this phase, Athena already had rich async export lifecycle controls:

- start
- list
- status
- cancel
- retry
- retry-terminal
- dry-run
- cleanup
- download

But the task surface still lacked the most practical piece of metadata: the normalized request snapshot.

Internally, `OpsRecoveryController` already persisted:

- `exportType`
- `days`
- `limit`
- `mode`
- `actor`
- `eventType`
- compare breakdown / actor limits and sorts

Yet none of that was visible in the operator surface. That meant:

- deduplicated tasks could be reused without showing why they matched
- retry responses could not clearly communicate what request they re-created
- the task table in `PreviewDiagnosticsPage` could not tell operators what each task actually represented

## Scope

### Backend

Updated [OpsRecoveryController.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/controller/OpsRecoveryController.java):

- added `RecoveryHistoryExportAsyncRequestSnapshotDto`
- `RecoveryHistoryExportAsyncCreateResponseDto` now includes `request`
- `RecoveryHistoryExportAsyncStatusResponseDto` now includes `request`
- `toHistoryExportAsyncCreateResponse(...)` now maps the persisted snapshot into the create response
- `toHistoryExportAsyncStatusResponse(...)` now maps the same snapshot into task status/list responses

This means these endpoints now expose normalized request scope:

- `POST /api/v1/ops/recovery/history/export-async`
- `GET /api/v1/ops/recovery/history/export-async`
- `GET /api/v1/ops/recovery/history/export-async/{taskId}`
- `POST /api/v1/ops/recovery/history/export-async/{taskId}/cancel`

Focused MVC assertions were updated in [OpsRecoveryControllerSecurityTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/controller/OpsRecoveryControllerSecurityTest.java), and the dependent async task center fixture was updated in [AsyncTaskLifecycleServiceTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/asynctask/AsyncTaskLifecycleServiceTest.java).

### Frontend

Updated [opsRecoveryService.ts](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/services/opsRecoveryService.ts):

- added `RecoveryHistoryExportAsyncRequestSnapshot`
- async create/status task types now expose `request`

Added [opsRecoveryAsyncTaskUtils.ts](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/utils/opsRecoveryAsyncTaskUtils.ts):

- formats a compact primary task scope label
- formats a secondary filter/detail line
- handles compare-specific knobs like breakdown/actor limits

Updated [PreviewDiagnosticsPage.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx):

- async export task table now has a dedicated `Request` column
- each row shows:
  - primary normalized scope summary
  - secondary filter/sort details when present
- start/retry toasts now reuse the same request summary, so deduplicated and retried tasks are easier to interpret immediately

## Files

### Backend

- `ecm-core/src/main/java/com/ecm/core/controller/OpsRecoveryController.java`
- `ecm-core/src/test/java/com/ecm/core/controller/OpsRecoveryControllerSecurityTest.java`
- `ecm-core/src/test/java/com/ecm/core/asynctask/AsyncTaskLifecycleServiceTest.java`

### Frontend

- `ecm-frontend/src/services/opsRecoveryService.ts`
- `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`
- `ecm-frontend/src/utils/opsRecoveryAsyncTaskUtils.ts`
- `ecm-frontend/src/utils/opsRecoveryAsyncTaskUtils.test.ts`

## Outcome

Athena’s async `OpsRecovery` task center is no longer just a lifecycle/status board.

It now behaves like a proper operator work surface:

- task rows explain their request scope
- deduplicated task reuse is understandable
- retry-created tasks preserve visible request context
- the UI and backend now share the same normalized request snapshot contract
