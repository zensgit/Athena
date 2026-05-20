# nodeService batch-download async Shape Guards: Design and Verification

Date: 2026-05-19

## Scope

Second nodeService sub-slice of the frontend service response-shape guard
backlog. The shared bundle introduced in the relations/renditions slice
(`NODE_UNEXPECTED_RESPONSE_MESSAGE`, `isObject`, `isFiniteNumber`,
`isStringOrNullish`, `isBooleanOrNullish`, `assertUnexpectedResponse`,
`isNullishOr`, `assertResponse`, `assertResponseArray`, `assertPageResponse`)
is reused; this slice adds an `isStringArray` helper and the per-DTO
predicates for batch-download-async, and converts only the 9 JSON-returning
batch-download-async methods (nodeService.ts ~1520-1582). No other
nodeService method or subdomain was touched.

Guarded methods: `startBatchDownloadAsync`, `preflightBatchDownloadAsync`,
`listBatchDownloadAsyncTasks`, `getBatchDownloadAsyncTask`,
`getBatchDownloadAsyncSummary`, `cancelBatchDownloadAsyncTask`,
`cleanupBatchDownloadAsyncTasks`, `cancelActiveBatchDownloadAsyncTasks`,
`cleanupBatchDownloadAsyncTask`.

Out of scope: backend; endpoint paths, payloads, query params (byte-for-byte
unchanged); `downloadBatchDownloadAsyncTask` (`api.downloadFile`,
`Promise<void>`); all other nodeService methods and subdomains; pages; e2e;
migrations; `.env` (neither staged nor modified).

Done directly on `main` (no worktree/cherry-pick; the records/mail
integration gate is satisfied and there is no parallel work).

## Gate Rulings Applied

- **G1** — `getBatchDownloadAsyncTask` has no current consumer but is
  retained and guarded for async-task-center API-surface consistency (same
  posture as D3 from sub-slice 1).
- **G2 — semantic tightening, NOT zero visible change** (gate framing):
  - `FileBrowser` `preflight` / `start` malformed → outer `try`/`catch`
    drops through to the synchronous ZIP fallback path (`downloadNodesAsZip`)
    rather than rendering garbage from a malformed body.
  - `FileBrowser` / `AdminDashboard` `listBatchDownloadAsyncTasks` and
    `getBatchDownloadAsyncSummary` malformed → outer `try`/`catch` clears
    the list panel / shows an error toast instead of the prior defensive
    `Array.isArray(?.items) ? : []` masking a malformed object.
  - `cancel*` / `cleanup*` malformed → existing `catch → toast.error`.
  These are all stricter than the prior behavior (which silently absorbed
  malformed bodies via consumer-side defensiveness); the slice does not
  add silent degradation in the service itself.
- **G3** — union-typed wire fields (`status: BatchDownloadAsyncStatus |
  string`, `decision: BatchDownloadPreflightDecision | string`,
  `primaryReason: ... | string`, `outcome: BatchDownloadPreflightOutcome |
  string`) are validated as plain strings — the enum membership is not
  checked.
- **G4 — lightweight deep validation**: `BatchDownloadAsyncTask.nodeIds`
  is required `string[]` (new `isStringArray` helper);
  `BatchDownloadPreflightResponse.items` (deep-validated as
  `BatchDownloadPreflightItem[]`) and `BatchDownloadAsyncTaskListResponse.items`
  (deep-validated as `BatchDownloadAsyncTask[]`); `paging?` on the list
  response, when present, is deep-validated (`maxItems`/`skipCount`/
  `totalItems`/`hasMoreItems`); `archiveSizeBytes?: number | null` uses
  `isNullishOr(value, isFiniteNumber)`. No bare `isObject` passes through
  a UI-read nested object.

## Gate Corrections Applied

- **No `assertPageResponse`** — `BatchDownloadAsyncTaskListResponse` is
  `items`/`totalCount`/`activeCount`/`paging`, not the
  `content`/`totalElements`/... `PageResponse` shape. A dedicated
  `isBatchDownloadAsyncTaskListResponse` predicate is used.
- **Single cleanup guard, tolerant of both shapes.** Bulk cleanup carries
  `statusFilter` and omits `taskId`; single-task cleanup carries `taskId`
  and omits `statusFilter`. Because both fields are optional on the wire
  DTO, one predicate handles both; the test suite covers both shapes
  separately.
- **`downloadBatchDownloadAsyncTask` is untouched.** Confirmed
  byte-identical (`api.downloadFile`, `Promise<void>`).
- **The 3-arg `api.post(url, undefined, { params })` shape is test-locked**
  for `cleanupBatchDownloadAsyncTasks` and
  `cancelActiveBatchDownloadAsyncTasks` via `toHaveBeenLastCalledWith` — so
  the explicit `undefined` body cannot drift to `{}` or be dropped.
- **Code + test staged together in the `fix` commit** (per
  `feedback_per_slice_fix_commit_stages_code_and_test`); `git status` was
  re-checked for zero in-scope `??` before committing.

## Implementation Notes

- Each method converted from `api.<verb><T>(...)` to `api.<verb><unknown>(...)`
  followed by `assertResponse(result, isX)`; endpoints, query params, and
  request bodies (including the explicit `undefined` body for the bulk
  cleanup / cancel-active POSTs) are byte-for-byte unchanged.
- All 9 methods are plain pass-throughs in the service — no client-side
  mapping — so there is no do-not-regress-mapping concern (unlike the
  three methods in the relations/renditions sub-slice).
- The new helper `isStringArray` is reusable by later sub-slices.

## Verification

Targeted Jest (new suite plus the gate-required regression set):

```bash
cd ecm-frontend
CI=true npm test -- --runTestsByPath \
  src/services/nodeService.batchDownloadAsync.test.ts \
  src/services/nodeService.relationsRenditions.test.ts \
  src/services/nodeService.createFolder.test.ts \
  src/services/nodeService.recordProjection.test.ts --watchAll=false
```

Result:

```text
Test Suites: 4 passed, 4 total
Tests:       13 passed, 13 total
```

The new suite covers: valid-DTO passthrough with endpoint/param/payload
assertions for every method, including the 3-arg `(url, undefined, {params})`
post shape for `cleanupBatchDownloadAsyncTasks` and
`cancelActiveBatchDownloadAsyncTasks`, default `'archive'` name, and the
`limit→maxItems` / `query→q` param mapping on `listBatchDownloadAsyncTasks`;
the cleanup guard tolerating both bulk (`statusFilter`, no `taskId`) and
single-task (`taskId`, no `statusFilter`) shapes; absent and valid
`paging` on the list response; HTML/null/missing-field/bad-nested-item
(non-string-array `nodeIds`, malformed `preflight.items` element,
malformed `list.items` element, present-but-malformed `paging`, wrong
numeric type, missing required `message`) → throw
`NODE_UNEXPECTED_RESPONSE_MESSAGE`. The `relationsRenditions`,
`createFolder`, and `recordProjection` suites prove the shared-file edits
did not regress prior nodeService slices.

Frontend lint:

```bash
cd ecm-frontend
npm run lint
```

Result: PASS.

Frontend production build (authoritative typecheck — `react-scripts`,
what CI runs):

```bash
cd ecm-frontend
CI=true npm run build
```

Result: PASS. The new per-DTO predicates and the `isStringArray` helper
compile cleanly with no unused-symbol error.

Diff hygiene:

```bash
git diff --check -- . ':!.env'
```

Result: PASS. nodeService.ts: +151 / -9.

## Follow-Up

- Remaining nodeService sub-slices (one at a time, no big-bang): search/preview
  async, folder/node CRUD, lock/checkout, version/history, permissions. They
  reuse the now-extended bundle (`NODE_UNEXPECTED_RESPONSE_MESSAGE`, generic
  helpers, and the new `isStringArray`).
