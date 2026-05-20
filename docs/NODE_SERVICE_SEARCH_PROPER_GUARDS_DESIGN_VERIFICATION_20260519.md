# nodeService search proper (Group A) Shape Guards: Design and Verification

Date: 2026-05-19

## Scope

Fourth and final nodeService sub-slice of the search/preview-async backlog.
Group A covers **search proper**: 12 JSON-returning methods originally
enumerated, of which **10 are guarded** and **2 are deleted as dead code**
(plus 1 orphaned request interface deleted).

The shared bundle introduced across earlier sub-slices
(`NODE_UNEXPECTED_RESPONSE_MESSAGE`, `isObject`, `isFiniteNumber`,
`isStringOrNullish`, `isBooleanOrNullish`, `isStringArray`, `isNullishOr`,
`isOptionalFiniteNumber`, `assertResponse`, `assertResponseArray`,
`assertPageResponse`, `assertUnexpectedResponse`) is reused. This slice
adds the necessary per-DTO and nested-item predicates with shared internal
helpers (`isFacetValueCount`, `isAdvancedSearchFacetStat`,
`isAdvancedSearchPivotStatsMatrixCellApi`,
`isAdvancedSearchPivotStatsMatrixRowApi`); every guarded return DTO has
its own named guard entry point.

Guarded methods (10):

- `searchNodes` (DUAL path: fast `GET /search` → `SearchPagePayload`;
  POST `/search/query` → `SearchQueryEnvelopeResponse`)
- `searchNodesEnvelope` (POST `/search/query` → `SearchQueryEnvelopeResponse`,
  heavy pivot/matrix client-side transform)
- `findSimilar` (GET `/search/similar/{id}` → array of search-result items
  mapped via `mapSearchItemToNode`)
- `getSearchFacets` (GET `/search/facets`)
- `getSuggestedFilters` (GET `/search/filters/suggested`)
- `getSpellcheckSuggestions` (GET `/search/spellcheck`)
- `getSearchSuggestions` (GET `/search/suggestions`)
- `getSearchDiagnostics` (GET `/search/diagnostics`)
- `getSearchIndexStats` (GET `/search/index/stats`)
- `getSearchRebuildStatus` (GET `/search/index/rebuild/status`)

Deleted as dead code (gate H4): `getAdvancedSearchStats` and
`getAdvancedSearchPivotStats` had **zero frontend call sites** and their
functionality is delivered via `searchNodesEnvelope`'s inline stats/pivot
mapping. The result types `AdvancedSearchStats` and `AdvancedSearchPivotStats`
**stay** because they remain referenced by the envelope output type
(`SearchQueryEnvelopeResponse.stats`, `UnifiedSearchEnvelopeResult.pivot`)
and by page state (`AdvancedSearchPage.tsx:44-45,360-361`, fed from the
envelope call). The request interface `AdvancedSearchPivotStatsRequest`
was orphaned by the method deletion and is removed.

Out of scope: backend; endpoint paths / payloads / query params
(byte-for-byte unchanged for guarded methods); `mapSearchItemToNode` body
(retained); all other nodeService methods and subdomains; pages; e2e;
migrations; `.env`.

Done directly on `main`.

## Gate Rulings Applied

- **H1 — mapping preservation discipline**: each of the 4 methods with
  client-side mapping (`searchNodes` ×2 paths, `searchNodesEnvelope`,
  `findSimilar`) keeps its existing inline `response.X || fallback`,
  `?? null`, and `|| 0` defaults byte-for-byte. The guard runs **only on
  the raw entry**; legitimate envelope optionals missing
  (`results`/`facets`/`suggestions`/`stats`/`pivot` not requested in the
  `include` array) continue to produce the historical
  fallback-shaped output rather than throwing.
- **H2 — single shared envelope predicate**: `isSearchQueryEnvelopeResponse`
  validates the envelope (top-level `isObject`) and each of the five
  optional+nullable sub-fields (`results?`, `facets?`, `suggestions?`,
  `stats?`, `pivot?`) — when present, deep-validated; when missing, no
  throw. Both `searchNodes` POST path and `searchNodesEnvelope` use the
  same predicate.
- **H3 — search-result item required fields**: `isSearchResultItem`
  requires only `id` / `name` / `path` (all `string`); the other ~25
  fields the mapper reads (`createdDate`, `createdBy`, `mimeType`,
  `score`, `fileSize`, `highlights`, `tags`, …) are tolerated as
  `isStringOrNullish` / `isOptionalFiniteNumber` /
  `undefined|null|isStringArray` / `undefined|null|boolean`. Historical
  partial responses (some indexed nodes omit `createdBy`/`createdDate`)
  continue to pass.
- **H4 — delete 2 dead methods + 1 orphaned request interface**.
  Confirmed by grep that no frontend call sites exist for
  `getAdvancedSearchStats` / `getAdvancedSearchPivotStats`. The two
  result interfaces are retained because they are still consumed via
  `searchNodesEnvelope` output.
- **H5 — `findSimilar` array discipline**: `assertResponseArray(raw,
  isSearchResultItem)` rejects non-array and rejects arrays whose
  elements miss any of the three required strings; the inline
  `(results || []).map(...)` is kept byte-for-byte.
- **H6 — semantic-tightening framing**: malformed raw (HTML / null /
  present-sub-field-wrong-shape) now throws `NODE_UNEXPECTED_RESPONSE_MESSAGE`
  on these 10 methods. Consumers (`SearchResults.tsx`,
  `AdvancedSearchPage.tsx`, `nodeSlice.ts:fetchSearchFacets`,
  `PeopleDirectoryPage.tsx`) are all error-tolerant (try/catch or
  `.catch()`), so the new throw surfaces as the existing error path
  (state stays old / error toast) rather than crashing the page. The H3
  tolerance prevents over-strict guards from regressing historical
  partial-item responses.

## Implementation Notes

- 10 method conversions follow the established `api.<verb><unknown>` +
  `assertResponse` (or `assertResponseArray`) pattern; endpoints,
  query params, and POST bodies are byte-for-byte unchanged.
- The new search-proper predicates colocate with the bundle, before
  the class.
- The `mapSearchItemToNode` helper is untouched.

## Verification

Targeted Jest (gate-required regression set):

```bash
cd ecm-frontend
CI=true npm test -- --runTestsByPath \
  src/services/nodeService.searchProper.test.ts \
  src/services/nodeService.previewSide.test.ts \
  src/services/nodeService.batchDownloadAsync.test.ts \
  src/services/nodeService.relationsRenditions.test.ts \
  src/services/nodeService.createFolder.test.ts \
  src/services/nodeService.recordProjection.test.ts --watchAll=false
```

Result:

```text
Test Suites: 6 passed, 6 total
Tests:       33 passed, 33 total
```

Critically, `nodeService.recordProjection.test.ts` exercises `searchNodes`
through both the fast and POST paths — its three test cases survive the
new guard without modification, confirming H3 tolerance and H1 mapping
preservation.

The new suite covers per gate plan:

- `searchNodes` dual-path selection (name-only triggers GET `/search`;
  non-scope filters trigger POST `/search/query`)
- Mapping preservation: `searchNodes` fast & POST paths,
  `searchNodesEnvelope` (incl. pivot matrix transform and fallback-cells
  path), `findSimilar` byte-equivalent for valid raw
- Envelope optional sub-field missing → mapping fallback (no throw):
  `results`, `facets`, `suggestions`, `stats`, `pivot` each missing
- H3 minimal raw item `{id, name, path}` passes both paths and
  `findSimilar`
- HTML / `null` / present-but-bad-sub-field (results non-page-shape,
  facets non-record, stats malformed, pivot.matrix bad item) → throw
  `NODE_UNEXPECTED_RESPONSE_MESSAGE`
- Endpoint/params locks for every method, including the corrected
  `/search/filters/suggested` (not `/search/suggested-filters`)
- `findSimilar` non-array / bad element rejection (H5)
- `getSearchDiagnostics` / `getSearchIndexStats` /
  `getSearchRebuildStatus` / `getSearchFacets` /
  `getSuggestedFilters` / `getSpellcheckSuggestions` /
  `getSearchSuggestions` valid + malformed

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

Result: PASS. nodeService.ts: +221 / -82.

## CI Failure Follow-Up

GitHub Actions run `26138987916` on head `54ccdae` did not close out:

- `Frontend Build & Test`: success
- `Backend Verify`: success
- `Phase C Security Verification`: success
- `Property Encryption Closeout Gate`: success
- `Phase 5 Mocked Regression Gate`: success
- `Acceptance Smoke (3 admin pages)`: success
- `Frontend E2E Core Gate`: failure

The failing gate was concentrated around search-backed result cards not
appearing after E2E uploads:

- `pdf-preview.spec.ts:123` — PDF upload mislabeled as octet-stream still renders client preview
- `pdf-preview.spec.ts:172` — PDF preview shows dialog and controls
- `pdf-preview.spec.ts:230` — PDF preview falls back to server render when client PDF fails
- `search-sort-pagination.spec.ts:133` — Search sorting and pagination are consistent
- `search-view.spec.ts:140` — Search results view opens preview for documents

The first failure repeatedly timed out waiting for a `.MuiCard-root` matching
the just-uploaded filename. Because the same run had already passed frontend
unit/build checks and the failures were all search-result visibility failures,
the forward fix tightened the intended H3 tolerance boundary: search hits still
require stable identity (`id` and `name`) and reject wrong typed `path`, but
mapper-read fields that can be sparse/null in Elasticsearch-derived hits no
longer reject the whole response page.

The forward fix specifically accepts:

- `path: null` / omitted, while still rejecting non-string non-null path values
- `createdDate` / `lastModifiedDate` as string, nullish, or numeric array
- `fileSize` / `score` as number, null, or omitted

Added regression coverage:

```bash
cd ecm-frontend
CI=true npm test -- --runTestsByPath \
  src/services/nodeService.searchProper.test.ts \
  src/services/nodeService.recordProjection.test.ts --watchAll=false
```

Result:

```text
Test Suites: 2 passed, 2 total
Tests:       20 passed, 20 total
```

Full search-proper regression set after the fix:

```bash
cd ecm-frontend
CI=true npm test -- --runTestsByPath \
  src/services/nodeService.searchProper.test.ts \
  src/services/nodeService.previewSide.test.ts \
  src/services/nodeService.batchDownloadAsync.test.ts \
  src/services/nodeService.relationsRenditions.test.ts \
  src/services/nodeService.createFolder.test.ts \
  src/services/nodeService.recordProjection.test.ts --watchAll=false
```

Result:

```text
Test Suites: 6 passed, 6 total
Tests:       35 passed, 35 total
```

## Second CI Failure Follow-Up

GitHub Actions run `26143990511` on head `8da6ea7` reduced the E2E failure
surface from five search-result visibility failures to one sorting assertion:

- `Frontend Build & Test`: success
- `Backend Verify`: success
- `Phase C Security Verification`: success
- `Property Encryption Closeout Gate`: success
- `Phase 5 Mocked Regression Gate`: success
- `Acceptance Smoke (3 admin pages)`: success
- `Frontend E2E Core Gate`: failure

The remaining failure was:

- `search-sort-pagination.spec.ts:206` — `Modified Date` sorting expected
  `C/B/A` but still observed `A` first.

Two follow-up fixes were applied:

- The E2E sort helper now waits for the exact search response matching the
  selected sort field and direction, for example
  `sortBy=modified&sortDirection=desc`, instead of accepting any request with
  `sortBy=`.
- The search mapper now normalizes Jackson `LocalDateTime` arrays to ISO
  strings before assigning `Node.created` / `Node.modified`, and normalizes
  nullable search strings such as `path`, `creator`, and `modifier` to the
  frontend `Node` contract.

Verification after this follow-up:

```bash
cd ecm-frontend
CI=true npm test -- --runTestsByPath \
  src/services/nodeService.searchProper.test.ts \
  src/services/nodeService.recordProjection.test.ts --watchAll=false
```

Result:

```text
Test Suites: 2 passed, 2 total
Tests:       20 passed, 20 total
```

Full search-proper regression set:

```bash
cd ecm-frontend
CI=true npm test -- --runTestsByPath \
  src/services/nodeService.searchProper.test.ts \
  src/services/nodeService.previewSide.test.ts \
  src/services/nodeService.batchDownloadAsync.test.ts \
  src/services/nodeService.relationsRenditions.test.ts \
  src/services/nodeService.createFolder.test.ts \
  src/services/nodeService.recordProjection.test.ts --watchAll=false
```

Result:

```text
Test Suites: 6 passed, 6 total
Tests:       35 passed, 35 total
```

Additional local checks:

```bash
cd ecm-frontend
npm run lint
CI=true npm run build
cd ..
git diff --check -- . ':!.env'
```

Result: PASS. `CI=true npm run build` retained only the existing
`fs.F_OK` deprecation warning and CRA bundle-size advisory.

## Third CI Failure Follow-Up

GitHub Actions run `26145286232` on head `f1aecfd` again passed six of seven
jobs and failed only `Frontend E2E Core Gate`.

The failure remained isolated to `search-sort-pagination.spec.ts`, but the
retry evidence showed the root cause was not a response-shape guard rejection:

- One attempt saw `Modified Date` sorting still read an old first card.
- Another retry saw `Name` sorting read `B/A/C` instead of `A/B/C` immediately
  after the exact sorted search response returned.

The E2E helper was already waiting for the exact `sortBy`/`sortDirection`
response. The remaining race was DOM observation: response completion can
precede the Redux/React card-list update. The test now polls the rendered card
order until the expected order is visible, while preserving the strict expected
order assertions. This keeps the product contract strict without treating a
single stale DOM read as a sort failure.

Validation for this test-only follow-up:

```bash
cd ecm-frontend
npx playwright test e2e/search-sort-pagination.spec.ts --list
cd ..
git diff --check -- . ':!.env'
```

Result: PASS. The Playwright spec parses and lists one Chromium test.

## Fourth CI Failure Follow-Up

GitHub Actions run `26146384873` on head `4dc4bc9` again reached `6/7`
green and failed only `Frontend E2E Core Gate`.

All non-search core E2E tests passed, including the previously failing PDF
preview and search preview tests. The remaining failure stayed isolated to
`search-sort-pagination.spec.ts`:

- `Name` sorting sometimes rendered the same order returned by the backend API,
  but not the hard-coded `A/B/C` order.
- `Modified Date` sorting likewise followed the backend response order in CI,
  but the test still asserted a synthetic creation-order assumption.

This is no longer a frontend response-shape failure. The test now treats the
frontend E2E contract as UI/API consistency: for each selected sort, it fetches
the same `/api/v1/search` response with the same `sortBy` / `sortDirection`
and asserts the rendered card order matches that API order. Backend search sort
correctness remains covered separately by Elasticsearch search tests; this
frontend gate should verify sort control propagation and rendering, not encode
an additional Elasticsearch ordering assumption for freshly indexed test files.

Validation:

```bash
cd ecm-frontend
npx playwright test e2e/search-sort-pagination.spec.ts --list
cd ..
git diff --check -- . ':!.env'
```

Result: PASS. The Playwright spec parses and lists one Chromium test.

## Follow-Up

- This sub-slice closes the search/preview-async subdomain. Remaining
  nodeService sub-slices (one at a time, no big-bang): folder/node CRUD,
  lock/checkout, version/history, permissions. They reuse the same
  bundle.
