# Ops Recovery Async Export-Tail Shape Guards: Design and Verification

Date: 2026-05-18

## Scope

This round extends the frontend service response-shape guard track to the
`opsRecoveryService` async export-tail, following the patterns established
for `ruleService`, `workflowService`, `recordsManagementService`,
`mailAutomationService`, and `bulkOperationService`.

The core/history methods of `opsRecoveryService` were guarded in an earlier
round; this slice covers only the previously-unguarded async export-tail
methods (`/ops/recovery/history/export-async*`).

The intent is defensive hardening against HTML fallback or malformed API
responses that Phase 5 Mocked frontend tests may otherwise miss
(see `feedback_phase5_mocked_html_fallback.md`).

This round did not change backend controllers, backend contracts, endpoint
paths, request payloads, query params, DTO wire shapes (the `export type`
declarations are untouched), Blob/download/void methods, package files,
migrations, pages, e2e tests, unrelated services, or `.env`. `.env` was
neither staged nor modified.

This slice was developed in an isolated worktree and is intentionally NOT
integrated into `main` until the parent records/mail guard round is
confirmed green in CI.

- Worktree: `/Users/chouhua/Downloads/Github/Athena-ops-recovery-async-tail-guards`
- Branch: `claude/ops-recovery-async-tail-guards-20260518`
- Base: `f653458` (clean; the async-tail methods are independent of the
  uncommitted records/mail changes, so this base is clean for later
  cherry-pick by the integration lane)

## Files Touched

Write set:

- `ecm-frontend/src/services/opsRecoveryService.ts`
- `ecm-frontend/src/services/opsRecoveryService.asyncTail.test.ts` (new —
  a dedicated file, deliberately NOT an extension of
  `opsRecoveryService.core.test.ts`, so this slice can be cherry-picked or
  reverted independently)
- `docs/OPS_RECOVERY_ASYNC_TAIL_SHAPE_GUARDS_DESIGN_VERIFICATION_20260518.md`
  (this file)

No other files were modified.

## Design

### Idiom reuse (no reinvention)

- Reuses the existing `OPS_RECOVERY_UNEXPECTED_RESPONSE_MESSAGE` sentinel.
  **No second sentinel was introduced.**
- Reuses the existing `assertOpsRecoveryResponse(condition): asserts
  condition` throw helper. The `(): never` style used by
  `bulkOperationService`/`recordsManagementService` was deliberately NOT
  used, because it does not match this file's established assertion style.
- Reuses the existing predicate bundle (`isRecord`, `isFiniteNumber`,
  `isNullableString`, `isOptionalNullableString`, `isOptionalString`,
  `isOptionalBoolean`, `isOptionalFiniteNumber`,
  `isOptionalNullableFiniteNumber`). No duplicate predicates were added.
- Each new `assertRecoveryHistoryExportAsync*` mirrors the existing
  `assertRecoveryBatchResult` shape: `isRecord` gate, per-field assertions,
  deep-validated nested arrays via per-item asserts, `return { ...value,
  <mappedArrays> }` or `return value as unknown as T`.

### Guarded methods (12)

All twelve JSON-returning async-tail methods were converted from
`api.<verb><T>(...)` to `api.<verb><unknown>(...)` followed by the matching
`assertRecoveryHistoryExportAsync*`, with endpoint, query params, and
request payloads byte-for-byte unchanged:

`startHistoryExportAsync`, `listHistoryExportAsyncTasks`,
`getHistoryExportAsyncTask`, `getHistoryExportAsyncTaskSummary`,
`getHistoryExportAsyncTaskSummaryFiltered`, `cancelHistoryExportAsyncTask`,
`retryHistoryExportAsyncTask`, `retryTerminalHistoryExportAsyncTasks`,
`dryRunRetryTerminalHistoryExportAsyncTasks`,
`retryTerminalHistoryExportAsyncTasksByTaskIds`,
`cancelActiveHistoryExportAsyncTasks`, `cleanupHistoryExportAsyncTasks`.

The `retryTerminalHistoryExportAsyncTasksByTaskIds` request-side
trim/dedupe/empty-filter payload normalization is do-not-regress: the
payload-building logic is byte-identical; only the response is guarded.

### `request` snapshot — lightweight deep validation (gate ruling)

`RecoveryHistoryExportAsyncRequestSnapshot` (carried by the Create and
TaskStatus DTOs) is consumed by the UI for display. A bare `isRecord`
guard would let `{ mode: {} }` reach the UI and render as
`[object Object]`. Per the gate ruling it is validated as
`undefined | null | record-with-typed-fields`:

- string-ish fields (`exportType`, `mode`, `actor`, `eventType`,
  `compareBreakdownSort`, `compareActorSort`) → `isOptionalNullableString`
- numeric fields (`limit`, `days`, `compareBreakdownLimit`,
  `compareActorLimit`) → `isOptionalFiniteNumber` (per the wire type these
  are not nullable; a `null` is rejected)

### Out of scope (intentionally unchanged)

- `exportDryRunRetryTerminalHistoryExportAsyncTasks` (`api.downloadFile`,
  `Promise<void>`)
- `downloadHistoryExportAsyncTask` (`api.getBlob`, `Promise<Blob>`)

## Verification

Targeted Jest (async-tail slice plus the existing core suite, to prove the
shared-file edits did not regress the already-guarded core methods):

```bash
cd ecm-frontend
CI=true npm test -- --runTestsByPath \
  src/services/opsRecoveryService.asyncTail.test.ts \
  src/services/opsRecoveryService.core.test.ts --watchAll=false
```

Result:

```text
Test Suites: 2 passed, 2 total
Tests:       37 passed, 37 total
```

The async-tail suite covers, per representative method: valid DTO
passthrough for all twelve methods; HTML fallback and `null` rejection for
GET/POST/array methods; missing-required-field, wrong-type, and malformed
nested-array-entry rejection; malformed `paging` rejection;
omitted-optional/nullable acceptance and optional `reasonBreakdown`
(present-valid, present-malformed); the `request` lightweight-deep ruling
(undefined/null/well-typed accepted, `{ mode: {} }` rejected, numeric
`null` rejected); and the gate request invariants — summary vs filtered
summary same endpoint with params only on the filtered call,
`listHistoryExportAsyncTasks` skipCount normalization with unchanged
maxItems/limit defaults, and `retryTerminalHistoryExportAsyncTasksByTaskIds`
trim/dedupe/empty-filter payload.

Frontend lint:

```bash
cd ecm-frontend
npm run lint
```

Result: PASS (no warnings or errors).

Frontend production build:

```bash
cd ecm-frontend
CI=true npm run build
```

Result: PASS. The `assertOpsRecoveryResponse(Array.isArray(...))`
narrowing and the `value as unknown as T` casts compile cleanly under the
`CI=true` build with no unused-symbol error.

Notes:

- Build still emits the existing CRA bundle-size advisory and the Node
  `fs.F_OK` deprecation warning. Neither blocks the build; both are
  documented in the sibling guard rounds.
- `node_modules` for the worktree was symlinked from the primary checkout
  to avoid a cold install stall (see
  `feedback_parallel_worktree_cold_cache_stall.md`).
- Backend CI, mocked regression gate, and Frontend E2E gate were not
  re-run; they are unaffected because no backend code, pages, e2e tests,
  or `.env` were touched.

Diff hygiene:

```bash
git diff --check -- . ':!.env'
```

Result: PASS.

## Follow-Up

- This branch is held out of `main` until the parent records/mail guard
  round is confirmed green in CI; the integration lane cherry-picks it
  afterward.
- Remaining service-guard candidates: `tagService` (the two untyped
  `findNodesByTag` / `findNodesByTags` methods) and `nodeService` split
  into thematic sub-slices.
