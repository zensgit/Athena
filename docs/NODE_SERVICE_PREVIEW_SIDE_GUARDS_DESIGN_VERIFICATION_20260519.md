# nodeService preview-side Shape Guards: Design and Verification

Date: 2026-05-19

## Scope

Third nodeService sub-slice of the frontend service response-shape guard
backlog. The subdomain "search/preview async" was found by `grep` to contain
27 JSON methods spanning four natural groups; per gate decision the round
covers only the **preview side** (groups B+C+D = 14 methods, nodeService.ts
~2187–2336). Group A (search proper, 12 methods) is deferred to the next
round.

The shared bundle introduced in the relations/renditions slice plus the
`isStringArray` helper added in batch-download are reused. This slice adds
the `isOptionalFiniteNumber` helper (non-nullable optional number) and the
preview-side per-DTO / nested-item predicates, and converts the 14
preview-side JSON methods to `api.<verb><unknown>` + `assertResponse`. No
other nodeService method or subdomain was touched.

Guarded methods:

- B (single-node, 4): `queuePreview`, `cancelQueuedPreview`, `repairPreview`,
  `queueOcr`
- C (by-search direct, 3): `getPreviewQueueBySearchCapabilities`,
  `queueFailedPreviewsBySearch`, `dryRunFailedPreviewsBySearch`
- D (by-search CSV-export async-tail, 7):
  `startDryRunFailedPreviewsCsvExportAsyncBySearch`,
  `getDryRunFailedPreviewsCsvExportAsyncBySearchTask`,
  `listDryRunFailedPreviewsCsvExportAsyncBySearchTasks`,
  `getDryRunFailedPreviewsCsvExportAsyncBySearchTasksSummary`,
  `cleanupDryRunFailedPreviewsCsvExportAsyncBySearchTasks`,
  `cancelActiveDryRunFailedPreviewsCsvExportAsyncBySearchTasks`,
  `cancelDryRunFailedPreviewsCsvExportAsyncBySearchTask`

Out of scope: backend; endpoint paths, payloads, query params
(byte-for-byte unchanged); `exportDryRunFailedPreviewsCsvBySearch` (line
2491, `Promise<Blob>`); `downloadDryRunFailedPreviewsCsvExportAsyncBySearch`
(line 2586, `Promise<Blob>`); **search proper (Group A, 12 methods)**, to
be its own sub-slice; all other nodeService methods and subdomains; pages;
e2e; migrations; `.env` (neither staged nor modified).

Done directly on `main`.

## Gate Rulings Applied

- **H1 — body/options/params shapes locked verbatim, default vs non-default
  branches both tested:**
  - `null` body: methods 1, 3, 4, 12 (POST(url, null, {params}))
  - `{}` body: method 13 (POST(url, {}, {params}))
  - 2-arg POST(url, payload): methods 6, 7, 8
  - 1-arg POST(url) / GET(url): methods 2, 5, 9, 14
  - params conditional: method 10 (`{limit, ...(status ? {status} : {})}`),
    methods 11/12/13 (`status ? {status} : undefined`)
  - default/non-default branches: `queuePreview`/`queueOcr` force=false vs
    true; `repairPreview` no-options (all three default `true` via `?? true`)
    vs `{forceInvalidate:false}` (others default true) vs all-explicit-false
  - list/summary/cleanup/cancelActive each tested both with and without
    status
- **H2 — Task / TaskStatus predicates kept independent (per-DTO discipline)
  but delegate to a shared `isExportAsyncTaskShape` inner helper.**
  `error / message / finishedAt / filename` (and `status / createdAt`) stay
  optional — the backend omits them in QUEUED/RUNNING states. The minimal
  `{ taskId: 't1' }` is a valid Task / TaskStatus.
- **H3 — `/documents/{id}` prefix asserted via `toHaveBeenLastCalledWith` on
  all four B-group methods**, locking it against future drift to `/nodes/`
  or `/api/`.
- **H4 — lightweight deep validation:**
  - `workerCount?: number` and `scanSkipped?: number` on `BatchResult` and
    `DryRunResult`, plus `attempts?: number` on `PreviewQueueStatus` and
    `OcrQueueStatus`, are validated as **non-nullable optional finite
    numbers** via the new `isOptionalFiniteNumber` helper. `null` is
    rejected — the wire types declare `?: number`, not `?: number | null`.
  - Required nested arrays (`reasonBreakdown`, `results`, `samples`,
    `items`) are deep-validated element by element.
  - Optional nested arrays (`skipBreakdown?`) accept `undefined`; when
    present, every element is deep-validated.

## Wire-DTO Correction

Renamed the optional field on
`PreviewQueueSearchDryRunExportAsyncTaskCancelActiveResult`
(nodeService.ts:648-654) from `status?: string | null` to
`statusFilter?: string | null` to match the backend
`PreviewQueueBySearchDryRunExportAsyncCancelActiveResponse` record
(SearchController.java:856-861). The parallel cleanup record
(`PreviewQueueBySearchDryRunExportAsyncCleanupResponse`,
SearchController.java:849-854) intentionally uses **`status`** on the wire;
the corresponding frontend `PreviewQueueSearchDryRunExportAsyncTaskCleanupResult.status?`
field already matches and is left untouched. The asymmetry is preserved as
the backend authored it.

Consumer impact: the sole call site
(`AdvancedSearchPage:2018`) reads `response?.cancelledCount` and
`response?.message` — never `.status` / `.statusFilter` — so no consumer
breaks.

## Implementation Notes

- The slice adds the necessary per-DTO and nested-item predicates with
  shared internal helpers (`isReasonOrSkipCountShape`,
  `isExportAsyncTaskShape`); every return DTO has its own named guard
  entry point (`is*` predicate).
- New shared helper: `isOptionalFiniteNumber` (non-nullable optional finite
  number); reusable by later sub-slices.
- All 14 methods are plain pass-throughs in the service — no client-side
  mapping.

## Verification

Targeted Jest (gate-required regression set):

```bash
cd ecm-frontend
CI=true npm test -- --runTestsByPath \
  src/services/nodeService.previewSide.test.ts \
  src/services/nodeService.batchDownloadAsync.test.ts \
  src/services/nodeService.relationsRenditions.test.ts \
  src/services/nodeService.createFolder.test.ts \
  src/services/nodeService.recordProjection.test.ts --watchAll=false
```

Result:

```text
Test Suites: 5 passed, 5 total
Tests:       19 passed, 19 total
```

The new suite covers, per gate test plan:

- Every method's exact `toHaveBeenLastCalledWith` shape (null body, `{}`
  body, 2-arg POST, single-arg POST/GET, conditional-params variants), with
  the `/documents/{id}` prefix asserted for B-group methods.
- Default + non-default parameter branches: force/forceInvalidate/requeue/
  forceQueue, status given vs not given.
- Optional Task / TaskStatus fields confirmed staying optional via the
  minimal `{ taskId: 't1' }` shape.
- `workerCount` / `scanSkipped` present-and-finite accepted; `null` rejected
  (non-nullable optional).
- HTML / `null` / missing-required / wrong-type / bad-nested-element
  (results, reasonBreakdown, skipBreakdown present-but-bad, samples,
  items) all throw `NODE_UNEXPECTED_RESPONSE_MESSAGE`.

The existing `batchDownloadAsync` / `relationsRenditions` / `createFolder`
/ `recordProjection` suites prove the shared-file edits did not regress
prior nodeService slices.

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

Result: PASS.

Diff hygiene:

```bash
git diff --check -- . ':!.env'
```

Result: PASS. nodeService.ts: +270 / -15.

## Follow-Up

- Remaining nodeService sub-slices (one at a time, no big-bang): **search
  proper (Group A, 12 methods incl. searchNodes / searchNodesEnvelope /
  getAdvancedSearch{Stats,PivotStats} / findSimilar / getSearchFacets /
  getSuggestedFilters / getSpellcheckSuggestions / getSearchSuggestions /
  getSearchDiagnostics / getSearchIndexStats / getSearchRebuildStatus)**;
  then folder/node CRUD, lock/checkout, version/history, permissions. All
  reuse the bundle plus the new `isOptionalFiniteNumber` helper.
