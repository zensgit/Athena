# Bulk Operation Service Shape Guards: Design and Verification

Date: 2026-05-18

## Scope

This round extends the frontend service response-shape guard track to
`bulkOperationService`, following the patterns established for
`ruleService`, `workflowService`, `recordsManagementService`, and
`mailAutomationService`.

The intent is defensive hardening against HTML fallback or malformed API
responses that Phase 5 Mocked frontend tests may otherwise miss
(see `feedback_phase5_mocked_html_fallback.md`).

This round did not change backend controllers, backend contracts, endpoint
paths, request payloads, query params, DTO wire shapes, Blob/download/void
methods, package files, migrations, pages, e2e tests, unrelated services, or
`.env`. `.env` was neither staged nor modified.

This slice was developed in an isolated worktree and is intentionally NOT
integrated into `main` until the parent records/mail guard round is confirmed
green in CI.

- Worktree: `/Users/chouhua/Downloads/Github/Athena-bulk-operation-service-guards`
- Branch: `claude/bulk-operation-service-guards-20260518`
- Base: `f653458` (clean; `bulkOperationService.ts` is independent of the
  uncommitted records/mail changes, so this base is clean for later
  cherry-pick by the integration lane)

## Files Touched

Write set:

- `ecm-frontend/src/services/bulkOperationService.ts`
- `ecm-frontend/src/services/bulkOperationService.test.ts` (new — no
  co-located test existed)
- `docs/BULK_OPERATION_SERVICE_SHAPE_GUARDS_DESIGN_VERIFICATION_20260518.md`
  (this file)

No other files were modified.

## Behavior Classification

A single sentinel is exported:

- `BULK_OPERATION_UNEXPECTED_RESPONSE_MESSAGE`

A small helper bundle (`isObject`, `isFiniteNumber`, `isStringArray`,
`isStringRecord`, `assertUnexpectedResponse`) mirrors the sibling guard
services. No second sentinel was introduced.

### A. `bulkDelete` — tightened to throw

`bulkDelete` previously returned `api.post<BulkOperationResult>` directly. It
now calls `api.post<unknown>` and asserts every `BulkOperationResult` field:

- `operation`: string
- `totalRequested` / `successCount` / `failureCount`: finite number (three
  numeric fields — the wire DTO has three, not four)
- `successfulIds`: array of strings
- `failures`: object whose every value is a string

Any field-level mismatch, an HTML fallback string, or `null` throws
`BULK_OPERATION_UNEXPECTED_RESPONSE_MESSAGE`. `bulkDelete` consumers read the
outcome counts directly, so a malformed body must surface a recognizable
error rather than silently rendering garbage. Object-only validation was
explicitly rejected as too loose.

### B. `listHistory` / `listHistorySummary` / `listHistoryTrend`

These dashboard-style list/summary/trend panels intentionally degrade rather
than raise an error toast (mirrors `mailAutomationService.listProviderPresets`).

**B1 — status-quo equivalent, zero observable change (no regression, no
throw):**

- a valid top-level array (only `listHistory` has the array form)
- a valid response object and its fallback fields, which differ per method:
  - `listHistory`: page object (`items`/`content`/`total`/`totalElements`/
    `page`/`number`/`size` — paginated)
  - `listHistorySummary`: summary response object (`total`/`totalEvents`/
    `eventTypeItems`/`items`/`actorItems` — not paginated, different shape
    from `listHistory`)
  - `listHistoryTrend`: trend response object (`items`/`trend`/`truncated`/
    `scanLimit` — not paginated)
- a top-level HTML fallback string (already degraded to empty/zero before
  this round; behavior preserved byte-for-byte)
- objects missing top-level fields (still normalized via the existing
  `normalize*` helpers)

The existing inline defensiveness was extracted into named helpers with no
observable output change for any of the above inputs.

**B2 — deliberate improvements this round (explicitly NOT claimed as
status-quo equivalent):**

1. A top-level `null` (or any non-array/non-object such as number/boolean)
   previously threw a `TypeError` at `response.items` /
   `response.eventTypeItems`. It now degrades to the same empty/zero result
   as the response-object fallback path:
   - `listHistory` → `{ items: [], total: 0, page, size }`
   - `listHistorySummary` → `{ total: 0, eventTypeItems: [], actorItems: [] }`
   - `listHistoryTrend` → `{ items: [], truncated: false, scanLimit: null }`
2. Non-object entries inside the item arrays (`null` / string / primitive)
   previously threw a `TypeError` (null entry) or produced all-null fake
   records (primitive entry). They are now filtered out before `normalize*`;
   valid object entries are unchanged.

None of the three list methods throw the sentinel.

## Out of Scope

Intentionally unchanged:

- Blob/void download endpoints: `exportHistoryCsv`,
  `exportHistorySummaryCsv`, `exportHistoryTrendCsv` (`api.downloadFile`,
  `Promise<void>`, no JSON body).
- Endpoint paths, query parameters, request bodies — byte-for-byte
  unchanged.

## Verification

Targeted Jest:

```bash
cd ecm-frontend
CI=true npm test -- --runTestsByPath src/services/bulkOperationService.test.ts --watchAll=false
```

Result:

```text
Test Suites: 1 passed, 1 total
Tests:       10 passed, 10 total
```

The suite covers, per method: valid array / valid response object, HTML
fallback, `null` (asserting the deliberate non-throwing improvement),
non-object array entries (asserting filtering with no fake records and no
throw), missing-field objects, an empty `{}` object (asserting the
response-object fallback path stays status-quo equivalent, distinct from
the non-object short-circuit), and request-shape passthrough locked via
`toHaveBeenCalledWith` for all three list methods; plus `bulkDelete`
field-level malformations.

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

Result: PASS. The wire-shape wrapper interfaces remain referenced via
`as unknown as` casts (the house idiom), so the `CI=true` build raised no
unused-symbol error.

Notes:

- Build still emits the existing CRA bundle-size advisory and the Node
  `fs.F_OK` deprecation warning from the CRA toolchain. Neither blocks the
  build; both are documented in the sibling guard rounds.
- `node_modules` for the worktree was symlinked from the primary checkout
  to avoid a cold install stall (see
  `feedback_parallel_worktree_cold_cache_stall.md`).
- Backend CI, mocked regression gate, and Frontend E2E gate were not re-run;
  they are unaffected because no backend code, pages, e2e tests, or `.env`
  were touched.

Diff hygiene:

```bash
git diff --check -- . ':!.env'
```

Result: PASS.

## Follow-Up

- This branch is held out of `main` until the parent records/mail guard
  round is confirmed green in CI; the integration lane cherry-picks it
  afterward.
- Remaining service-guard candidates: `opsRecoveryService` async-export
  tail, `tagService` (the two untyped `findNodesByTag` / `findNodesByTags`
  methods), and `nodeService` split into thematic sub-slices.
