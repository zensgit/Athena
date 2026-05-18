# Preview Diagnostics Async Task Service Shape Guards: Design and Verification

Date: 2026-05-18

## Scope

This slice completes the `previewDiagnosticsService` frontend service
guard gap left by the previous core diagnostics pass. It hardens
JSON-returning async export task lifecycle and task-center helper methods
while preserving endpoint paths, payloads, query params, overload
behavior, download methods, and CSV export methods.

No backend code, UI page, route contract, package file, or migration was
changed. `.env` was already modified before this slice and was not
touched, staged, or committed.

Claude was attempted for a parallel `peopleService` slice but the Claude
CLI returned a quota-limit message before producing changes. A Codex
worker was started as a parallel fallback for `peopleService`; this
document covers only the local `previewDiagnosticsService` async task
slice.

## Guarded Methods

Rendition resources async export task lifecycle:

- `startRenditionResourcesExportTask`
- `listRenditionResourcesExportTasks`
- `getRenditionResourcesExportTaskSummary`
- `cleanupRenditionResourcesExportTasks`
- `cancelActiveRenditionResourcesExportTasks`
- `getRenditionResourcesExportTask`
- `cancelRenditionResourcesExportTask`
- `retryRenditionResourcesExportTask`
- `retryTerminalRenditionResourcesExportTasks`
- `dryRunRetryTerminalRenditionResourcesExportTasks`
- `retryTerminalRenditionResourcesExportTasksByTaskIds`

Queue diagnostics active cancellation:

- `cancelQueueDiagnosticsActive`

Queue declined async export task lifecycle:

- `startQueueDeclinedExportTask`
- `listQueueDeclinedExportTasks`
- `getQueueDeclinedExportTaskSummary`
- `cleanupQueueDeclinedExportTasks`
- `cancelActiveQueueDeclinedExportTasks`
- `getQueueDeclinedExportTask`
- `cancelQueueDeclinedExportTask`
- `retryQueueDeclinedExportTask`
- `retryTerminalQueueDeclinedExportTasks`
- `dryRunRetryTerminalQueueDeclinedExportTasks`
- `retryTerminalQueueDeclinedExportTasksByTaskIds`

Queue declined requeue dry-run async export task lifecycle:

- `startQueueDeclinedRequeueDryRunExportTask`
- `listQueueDeclinedRequeueDryRunExportTasks`
- `getQueueDeclinedRequeueDryRunExportTaskSummary`
- `cleanupQueueDeclinedRequeueDryRunExportTasks`
- `cancelActiveQueueDeclinedRequeueDryRunExportTasks`
- `getQueueDeclinedRequeueDryRunExportTask`
- `cancelQueueDeclinedRequeueDryRunExportTask`
- `retryQueueDeclinedRequeueDryRunExportTask`
- `retryTerminalQueueDeclinedRequeueDryRunExportTasks`
- `dryRunRetryTerminalQueueDeclinedRequeueDryRunExportTasks`
- `retryTerminalQueueDeclinedRequeueDryRunExportTasksByTaskIds`

Out of scope and intentionally unchanged:

- `downloadRenditionResourcesExportTask`
- `downloadQueueDeclinedExportTask`
- `downloadQueueDeclinedRequeueDryRunExportTask`
- CSV methods using `api.downloadFile`
- core diagnostics JSON methods already guarded in
  `PREVIEW_DIAGNOSTICS_SERVICE_CORE_SHAPE_GUARDS_DESIGN_VERIFICATION_20260518.md`

## Design

This slice reuses the existing exported sentinel:

- `PREVIEW_DIAGNOSTICS_UNEXPECTED_RESPONSE_MESSAGE`

Added runtime guards for:

- async export task base metadata
- task-center paging envelopes
- task list envelopes
- status-count summaries
- cleanup responses
- cancel-active responses
- retry-terminal responses
- retry-terminal dry-run responses and reason breakdowns
- queue diagnostics cancel-active responses and nested items

Guard policy:

- Reject HTML fallback and non-object JSON where objects are expected.
- Require task `taskId` to be a string.
- Allow task status/message/error/timestamp/filename fields to be
  nullable or absent when backend DTOs model them as optional.
- Require task-center counts to be finite numbers.
- Require paging `hasMoreItems` and optional task `deduplicated`/`force`
  flags to be booleans when present.
- Validate nested `items`, `results`, and optional `reasonBreakdown`
  arrays before returning them to UI callers.
- Preserve existing query trimming, task id encoding, overload behavior,
  default force semantics, and task-id deduplication.

## Test Coverage

New test file:

- `ecm-frontend/src/services/previewDiagnosticsService.async.test.ts`

Coverage added:

- Rendition async export task lifecycle success path and endpoint
  forwarding.
- Queue declined export task lifecycle success path and payload
  forwarding.
- Queue declined requeue dry-run export task lifecycle success path and
  payload forwarding.
- `cancelQueueDiagnosticsActive` success path and trimmed query params.
- HTML fallback rejection.
- Malformed task list paging rejection.
- Malformed summary count rejection.
- Malformed retry dry-run reason breakdown rejection.
- Malformed queue cancel-active item rejection.

## Verification

Targeted Jest:

```bash
cd ecm-frontend
npm test -- --runTestsByPath src/services/previewDiagnosticsService.core.test.ts src/services/previewDiagnosticsService.async.test.ts --watchAll=false
```

Result:

```text
PASS src/services/previewDiagnosticsService.async.test.ts
PASS src/services/previewDiagnosticsService.core.test.ts
Test Suites: 2 passed, 2 total
Tests:       69 passed, 69 total
```

Frontend lint:

```bash
cd ecm-frontend
npm run lint
```

Result: PASS.

Frontend CI build:

```bash
cd ecm-frontend
CI=true npm run build
```

Result: PASS.

Notes:

- Build emitted the existing CRA bundle-size advisory.
- Build emitted the existing Node deprecation warning for `fs.F_OK`.
- Neither warning blocked the build.

Diff hygiene:

```bash
git diff --check
```

Result: PASS.

## Commit

- `209a6ab fix(preview-diagnostics): guard async task responses`

## Follow-Up

The next parallel target remains `peopleService` once an assistant lane
is available. If Claude quota is unavailable, use a Codex worker or do it
as the next local slice with the same final-integration discipline:
targeted Jest, lint, `CI=true npm run build`, and pushed CI confirmation.
