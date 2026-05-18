# Preview Diagnostics Core Service Shape Guards: Design and Verification

Date: 2026-05-18

## Scope

This slice hardens core JSON-returning methods in
`ecm-frontend/src/services/previewDiagnosticsService.ts` against
malformed runtime responses while preserving endpoint paths, request
payloads, query params, overload behavior, CSV/download methods, and
async export task lifecycle methods.

The initial implementation was developed in the parallel Claude
worktree `.claude/worktrees/claude-preview-diagnostics-core-service-guards`.
Claude stopped after hitting its budget limit, so the patch was treated
as an untrusted candidate: it was applied to main only after `git apply
--check`, target tests, lint review, and one Codex fix for a CI-breaking
unused type warning.

No backend code, UI page, route contract, package file, or migration was
changed. `.env` was already modified before this slice and was not
touched, staged, or committed.

## Guarded Methods

The guarded core JSON methods are:

- `listRecentFailures`
- `getFailureSummary`
- `getFailureLedger`
- `resetFailureLedger`
- `resetFailureLedgerBatch`
- `resetFailureLedgerByFilter`
- `getRenditionSummary`
- `getRenditionResources`
- `queueFailuresBatch`
- `getQueueDiagnosticsSummary`
- `getQueueDeclinedSummary`
- `requeueQueueDeclined`
- `dryRunQueueDeclinedRequeue`
- `clearQueueDeclined`
- `queueFailuresByReason`
- `getCadFailoverDiagnostics`
- `getTransformTraces`
- `getFailurePolicies`
- `updateFailurePolicy`
- `getRenditionPreventionBlocked`
- `unblockRenditionPrevention`
- `unblockAndRequeueRendition`
- `unblockRenditionPreventionBatch`
- `unblockAndRequeueRenditionBatch`
- `getDeadLetter`
- `replayDeadLetterBatch`
- `clearDeadLetterBatch`

Out of scope and intentionally unchanged:

- CSV/download methods using `api.downloadFile`
- rendition-resource async export task lifecycle methods
- task-center methods
- UI/e2e/backend behavior

## Design

Added the exported sentinel:

- `PREVIEW_DIAGNOSTICS_UNEXPECTED_RESPONSE_MESSAGE`

Added runtime guards for the major preview diagnostics DTO families:

- failure samples, summary counts, and ledger reset results
- rendition summary and resource payloads
- queue diagnostics, declined queue summaries, requeue dry-runs, clear
  results, and reason-based queue batches
- CAD failover diagnostics and transform traces
- failure policy reads/updates
- rendition prevention blocked/action/batch results
- dead-letter diagnostics, replay batches, and clear batches

Guard policy:

- Reject HTML fallback and non-object JSON where objects are expected.
- Require count fields to be finite numbers.
- Require booleans for flags such as `retryable`, `running`,
  `cancelRequested`, `builtIn`, `unblocked`, and `queued`.
- Allow backend nullable text/timestamp fields where DTOs already model
  `string | null`.
- Guard nested arrays before returning data to callers.
- Preserve existing argument defaults, query parameter trimming,
  overloaded queue-declined signatures, encoded document ids, and
  default `force` payload behavior.

`getRenditionResources` preserves the existing normalization contract:
it accepts both the wrapped diagnostics shape and the older plain array
shape, validates the selected items, then normalizes API item fields into
`PreviewRenditionResource`.

## Test Coverage

New test file:

- `ecm-frontend/src/services/previewDiagnosticsService.core.test.ts`

Coverage added:

- Success-path endpoint forwarding and response guards for every guarded
  method.
- HTML fallback rejection for representative get/put paths.
- Nested malformed payload rejection for summary counts, ledger items,
  queue items, queue-declined category counts, requeue dry-run
  breakdowns, transform trace events, failure policies, prevention
  actions, dead-letter items, replay results, and clear results.
- Overload coverage for `getQueueDeclinedSummary`,
  `requeueQueueDeclined`, `dryRunQueueDeclinedRequeue`, and
  `clearQueueDeclined`.
- Default payload behavior for `replayDeadLetterBatch` and
  `clearDeadLetterBatch`.

## Verification

Targeted Jest:

```bash
cd ecm-frontend
npm test -- --runTestsByPath src/services/previewDiagnosticsService.core.test.ts --watchAll=false
```

Result:

```text
PASS src/services/previewDiagnosticsService.core.test.ts
Test Suites: 1 passed, 1 total
Tests:       60 passed, 60 total
```

Frontend lint:

```bash
cd ecm-frontend
npm run lint
```

Result: PASS after removing the unused
`PreviewRenditionResourcesDiagnostics` type from the Claude candidate
patch.

Diff hygiene:

```bash
git diff --check
```

Result: PASS.

## Commit

Pending commit at document write time:

- `fix(preview-diagnostics): guard core service responses`

## Follow-Up

The async export task lifecycle and task-center helpers in
`previewDiagnosticsService.ts` remain outside this slice. They should be
handled in a separate pass because their response contracts differ from
the core diagnostics JSON endpoints and need their own fixtures.
