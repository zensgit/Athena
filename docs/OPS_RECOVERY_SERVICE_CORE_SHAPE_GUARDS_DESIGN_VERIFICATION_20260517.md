# Ops Recovery Service Core Shape Guards: Design and Verification

Date: 2026-05-17

## Scope

This first slice hardens the core JSON-returning recovery/history methods
on `ecm-frontend/src/services/opsRecoveryService.ts` against malformed
runtime responses, while preserving every existing endpoint path, method
name, request payload, query parameter, and default argument.

No backend code, no controller path, and no UI files were changed.

The slice is intentionally bounded:

- Out of scope this slice: `previewDiagnosticsService`,
  `contentArchiveService`, the async export task lifecycle methods
  (`startHistoryExportAsync`, `listHistoryExportAsyncTasks`,
  `getHistoryExportAsyncTask`, `getHistoryExportAsyncTaskSummary` and
  filtered variant, `cancelHistoryExportAsyncTask`,
  `retryHistoryExportAsyncTask`, `retryTerminalHistoryExportAsyncTasks`,
  `dryRunRetryTerminalHistoryExportAsyncTasks`,
  `retryTerminalHistoryExportAsyncTasksByTaskIds`,
  `cancelActiveHistoryExportAsyncTasks`,
  `cleanupHistoryExportAsyncTasks`).
- Out of scope this slice: Blob/CSV download methods
  (`exportDryRunCsv`, `exportHistoryCsv`, `exportHistorySummaryCsv`,
  `exportHistorySummaryTrendCsv`, `exportHistorySummaryCompareCsv`,
  `exportHistorySummaryCompareBreakdownCsv`,
  `exportHistorySummaryCompareActorsCsv`,
  `exportDryRunRetryTerminalHistoryExportAsyncTasks`,
  `downloadHistoryExportAsyncTask`).
- No backend, no UI, no other services touched.
- `.env` was not modified, staged, or committed.

## Backend Contract

Backend controller (source of truth):

- `ecm-core/src/main/java/com/ecm/core/controller/OpsRecoveryController.java`

Controller mount and pre-authorization:

- `@RequestMapping("/api/v1/ops/recovery")`
- `@PreAuthorize("hasRole('ADMIN')")`

Frontend relative paths consumed by this slice (all preserved):

- `POST /ops/recovery/queue-by-reason`
- `POST /ops/recovery/queue-by-window`
- `POST /ops/recovery/replay-batch`
- `POST /ops/recovery/clear-batch`
- `POST /ops/recovery/clear-by-filter`
- `POST /ops/recovery/replay-by-filter`
- `POST /ops/recovery/dry-run`
- `GET  /ops/recovery/history`
- `GET  /ops/recovery/history/summary`
- `GET  /ops/recovery/history/summary/trend`
- `GET  /ops/recovery/history/summary/compare`
- `GET  /ops/recovery/history/summary/compare/breakdown`
- `GET  /ops/recovery/history/summary/compare/actors`

Defaults remain unchanged:

- `getHistory(limit = 20, days = 7, mode?, page = 0, actor?, eventType?)`
- `getHistorySummary(days = 7, mode?, actor?, eventType?)`
- `getHistorySummaryTrend(days = 7, mode?, actor?, eventType?)`
- `getHistorySummaryCompare(days = 7, mode?, actor?, eventType?)`
- `getHistorySummaryCompareBreakdown(days = 7, mode?, actor?, eventType?, limit = 10, sort = 'DELTA_ABS_DESC')`
- `getHistorySummaryCompareActors(days = 7, mode?, actor?, eventType?, limit = 10, sort = 'DELTA_ABS_DESC')`

Backend records cross-checked (declared inside `OpsRecoveryController`):

- `RecoveryBatchResponseDto` — `String domain`, `String mode`,
  `int windowDays`, `int maxDocuments`, `long totalCandidates`,
  `int scanned`, `int matched`, `boolean truncated`, `int requested`,
  `int deduplicated`, `int queued`, `int skipped`, `int failed`,
  `List<RecoveryBatchItemDto> results`, `String error` (nullable).
- `RecoveryBatchItemDto` — `UUID documentId`, `JobState jobState`,
  `String outcome`, `String message` (nullable),
  `String previewStatus` (nullable), `FailureCategory failureCategory`,
  `String previewFailureReason` (nullable),
  `String previewFailureCategory` (nullable),
  `LocalDateTime previewLastUpdated` (nullable),
  `int attempts`, `Instant nextAttemptAt` (nullable).
- `RecoveryDryRunResponseDto` — same numeric shape as the batch DTO
  plus `int estimatedQueued`, `int estimatedSkipped`,
  `int estimatedFailed`, `List<RecoveryDryRunItemDto> samples`,
  `String error` (nullable).
- `RecoveryDryRunItemDto` — `UUID documentId`, nullable `name`/`path`/
  `mimeType`/`previewStatus`/`previewFailureReason`/
  `previewFailureCategory`/`previewLastUpdated`,
  `FailureCategory failureCategory`, `JobState predictedState`,
  `String predictedOutcome`, `String predictedReason`.
- `RecoveryHistoryResponseDto` — `String domain`, `int windowDays`,
  `int limit`, `int page`, `int totalPages`, `long total`, nullable
  `modeFilter`/`actorFilter`/`eventTypeFilter`,
  `List<RecoveryHistoryItemDto> items`.
- `RecoveryHistoryItemDto` — `UUID id` (nullable), nullable `nodeId`/
  `nodeName`/`eventType`/`actor`/`details`/`previewStatus`/
  `previewFailureReason`/`previewFailureCategory`/
  `previewLastUpdated`/`eventTime`, `String mode`.
- `RecoveryHistorySummaryResponseDto` — `String domain`,
  `int windowDays`, nullable `modeFilter`/`actorFilter`/
  `eventTypeFilter`, `long total`,
  `List<RecoveryHistorySummaryItemDto> items`,
  `List<RecoveryHistoryActorSummaryItemDto> actorItems`.
- `RecoveryHistorySummaryItemDto` — `String eventType`, `String mode`,
  `long count`.
- `RecoveryHistoryActorSummaryItemDto` — `String actor`, `long count`.
- `RecoveryHistoryTrendResponseDto` — `String domain`,
  `int windowDays`, nullable `modeFilter`/`actorFilter`/
  `eventTypeFilter`, `long total`, `boolean truncated`,
  `List<RecoveryHistoryTrendItemDto> items`.
- `RecoveryHistoryTrendItemDto` — `String day`, `long count`.
- `RecoveryHistorySummaryCompareResponseDto` — `String domain`,
  `int windowDays`, `int previousWindowDays`, nullable `modeFilter`/
  `actorFilter`/`eventTypeFilter`, `long currentTotal`,
  `long previousTotal`, `long delta`, `Double deltaPercent` (nullable),
  `boolean compareAvailable`, `boolean truncated`.
- `RecoveryHistorySummaryCompareActorsResponseDto` /
  `RecoveryHistorySummaryCompareBreakdownResponseDto` — share
  `String domain`, `int windowDays`, `int previousWindowDays`,
  nullable filters, `boolean compareAvailable`, `boolean truncated`,
  `String sortBy`, `int requestedLimit`, `int totalItems`,
  `boolean limited`, and an `items` list.
- `RecoveryHistorySummaryCompareActorItemDto` — `String actor`,
  `long currentCount`, `long previousCount`, `long delta`,
  `Double deltaPercent` (nullable).
- `RecoveryHistorySummaryCompareBreakdownItemDto` —
  `String eventType`, `String mode`, plus the same numeric/delta shape.

UUIDs and timestamps are serialized as strings; numeric `int`/`long`
fields are serialized as JSON numbers.

## Design

Added the exported sentinel:

- `OPS_RECOVERY_UNEXPECTED_RESPONSE_MESSAGE` (`"Ops recovery endpoint
  returned an unexpected response. Mocked CI gate may not cover it;
  backend route may be missing."`).

Added small typed guards:

- `isRecord`, `isFiniteNumber`, `isNullableString`,
  `isOptionalNullableString`, `isOptionalString`, `isOptionalBoolean`,
  `isOptionalFiniteNumber`, `isOptionalNullableFiniteNumber`.
- `assertOpsRecoveryResponse(condition)`: `asserts condition` — throws
  with the exported sentinel when violated.

Added assertion functions for the DTOs consumed by the scoped methods:

- `assertRecoveryBatchItem`, `assertRecoveryBatchItems`,
  `assertRecoveryBatchResult`.
- `assertRecoveryDryRunItem`, `assertRecoveryDryRunResult`.
- `assertRecoveryHistoryItem`, `assertRecoveryHistoryResult`.
- `assertRecoveryHistorySummaryItem`,
  `assertRecoveryHistoryActorSummaryItem`,
  `assertRecoveryHistorySummaryResult`.
- `assertRecoveryHistoryTrendItem`,
  `assertRecoveryHistoryTrendResult`.
- `assertRecoveryHistorySummaryCompareResult`.
- `assertRecoveryHistorySummaryCompareActorItem`,
  `assertRecoveryHistorySummaryCompareActorsResult`.
- `assertRecoveryHistorySummaryCompareBreakdownItem`,
  `assertRecoveryHistorySummaryCompareBreakdownResult`.

Guard policy follows recently merged sibling services
(`opsPolicyService`, `dispositionScheduleService`, `tenantService`):

- Finite number guards for backend `int`/`long` counters
  (`windowDays`, `maxDocuments`, `totalCandidates`, `scanned`,
  `matched`, `requested`, `deduplicated`, `queued`, `skipped`,
  `failed`, `attempts`, `count`, `currentCount`, `previousCount`,
  `delta`, `currentTotal`, `previousTotal`, `windowDays`,
  `previousWindowDays`, `limit`, `page`, `totalPages`, `total`,
  `estimatedQueued`, `estimatedSkipped`, `estimatedFailed`,
  `requestedLimit`, `totalItems`).
- `null`-aware string guards for nullable timestamp/error fields
  (`error`, `message`, `previewStatus`, `previewFailureReason`,
  `previewFailureCategory`, `previewLastUpdated`, `nextAttemptAt`,
  `eventTime`, `details`, `actor`, `id`, `nodeId`, `nodeName`,
  `eventType`, `modeFilter`, `actorFilter`, `eventTypeFilter`).
- Optional-or-nullable number guard for `deltaPercent` (backend
  `Double` may be null when prior window is empty).
- Optional guards for the compare metadata fields (`sortBy`,
  `requestedLimit`, `totalItems`, `limited`) — the TS surface marks
  them optional even though the backend always emits them.
- String guards (without union narrowing) for fields whose frontend
  type is already a `'...' | string` extension union (`mode`,
  `outcome`, `sortBy`) or a known enum string serialization
  (`jobState`, `failureCategory`, `predictedState`,
  `predictedOutcome`, `predictedReason`).
- `Array.isArray` + `map(assertItem)` for every list field.

Scoped methods were rewritten to call `api.get<unknown>` /
`api.post<unknown>` and assert the body before returning the typed
shape. Every endpoint path, payload position, params object, and
default argument was preserved verbatim. Blob/CSV download methods and
async export task methods were left untouched in this slice.

## Test Coverage

New test file:

- `ecm-frontend/src/services/opsRecoveryService.core.test.ts`

The test file mocks `./api` (only `get` and `post`) and exercises every
scoped method:

Success forwarding:

- `queueByReason` — verifies path `/ops/recovery/queue-by-reason` and
  full payload forwarding.
- `queueByWindow`, `replayBatch`, `clearBatch`, `clearByFilter`,
  `replayByFilter` — each verifies its endpoint path, payload
  forwarding, and that the returned `mode` is the backend-emitted
  value (`QUEUE_BY_WINDOW`, `REPLAY_BATCH`, `CLEAR_BATCH`,
  `CLEAR_BY_FILTER`, `REPLAY_BY_FILTER`).
- `dryRun` — verifies path `/ops/recovery/dry-run` and payload
  forwarding.
- `getHistory` — verifies default args (`limit=20, days=7, page=0,
  mode=undefined, actor=undefined, eventType=undefined`) and custom
  args (`limit=50, days=14, mode='QUEUE_BY_REASON', page=2,
  actor='admin', eventType='OPS_RECOVERY_QUEUE_BY_REASON'`).
- `getHistorySummary` — verifies default args and custom args.
- `getHistorySummaryTrend` — verifies default args.
- `getHistorySummaryCompare` — verifies default args.
- `getHistorySummaryCompareBreakdown` — verifies default args
  (`limit=10, sort='DELTA_ABS_DESC'`) and custom args (`limit=25,
  sort='DELTA_DESC'`).
- `getHistorySummaryCompareActors` — verifies default args
  (`limit=10, sort='DELTA_ABS_DESC'`) and custom args (`limit=5,
  sort='CURRENT_DESC'`).

Negative coverage required by the brief:

- HTML fallback rejection on a POST endpoint
  (`queueByReason` receives `<!doctype html>...` and rejects with
  `OPS_RECOVERY_UNEXPECTED_RESPONSE_MESSAGE`).
- HTML fallback rejection on a GET endpoint (`getHistory` receives
  `<!doctype html>...` and rejects).
- Malformed nested result item (`queueByReason` returns
  `results[0].attempts = 'two'`).
- Malformed dry-run sample (`dryRun` returns
  `samples[0].predictedState = 42`).
- Malformed history item (`getHistory` returns
  `items[0].eventType = 42`).
- Malformed summary count (`getHistorySummary` returns
  `items[0].count = Number.NaN`).
- Malformed trend item (`getHistorySummaryTrend` returns
  `items[0].day = 42`).
- Malformed compare deltaPercent (`getHistorySummaryCompare` returns
  `deltaPercent = 'fast'`).
- Malformed compare breakdown item
  (`getHistorySummaryCompareBreakdown` returns
  `items[0].delta = 'big'`).
- Malformed compare actor item (`getHistorySummaryCompareActors`
  returns `items[0].currentCount = Number.NaN`).

## Verification

Local verification was run after temporarily symlinking this worktree's
`ecm-frontend/node_modules` to the main repo dependency tree. The
symlink was removed before staging.

- `cd ecm-frontend && npm test -- --runTestsByPath
  src/services/opsRecoveryService.core.test.ts --watchAll=false`
  - Result: PASS, 27 tests / 1 suite.
- `cd ecm-frontend && npm run lint`
  - Result: PASS.
- `git diff --check`
  - Result: PASS.

`CI=true npm run build` is intentionally deferred to the integration
pass after this worktree is merged with the parallel `contentArchive`
slice.

## Commit

- `fix(ops-recovery): guard core service responses`

## Notes

- `.env` was not touched, staged, or committed in this slice.
- `previewDiagnosticsService`, `contentArchiveService`, all async
  export task lifecycle methods, and all Blob/CSV download methods on
  `opsRecoveryService` remain unchanged; they will be hardened in
  later slices.
- Per the project memory note `[Parallel-worktree agents stall on
  cold caches]`, the canonical workaround is to pre-warm
  `node_modules` (or run verification from the main session). That
  workaround was unavailable here because the cross-directory
  symlink required out-of-band approval.
