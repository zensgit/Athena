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

## Follow-Up

- This sub-slice closes the search/preview-async subdomain. Remaining
  nodeService sub-slices (one at a time, no big-bang): folder/node CRUD,
  lock/checkout, version/history, permissions. They reuse the same
  bundle.
