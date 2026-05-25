# Saved-Search Results CSV Export (C1) — Verification

Date: 2026-05-25
Brief: `docs/SAVED_SEARCH_RESULTS_CSV_EXPORT_ADJUDICATION_AND_DESIGN_20260525.md`; candidate C1 in `docs/PRODUCT_CAPABILITY_DISCOVERY_REFRESH3_20260525.md`. Gate ruling: D1–D6 at brief defaults — one-shot export, default 1000 / hard cap 5000, per-row button on SavedSearchesPage, no scheduler/async/JSON.

## Production changes

### Backend
- `SavedSearchService.java` — `exportSavedSearchCsv(id, limit)`: owner-only (mirrors `executeSavedSearch`'s `userId` check → `SecurityException`), converts the stored `queryParams` → `FacetedSearchRequest`, sets the request `SimplePageRequest` page 0 / size = `min(limit, 5000)` (default 1000), runs `facetedSearchService.search`, and builds CSV from `Page<SearchResult>.getContent()` via a local RFC-4180 `csvEscape`/`appendCsvRow`. Columns: `Name, Path, Type, MIME Type, Size (bytes), Version, Created By, Created Date, Last Modified By, Last Modified Date`. Search failure → `RuntimeException` with a **fixed** message (cause logged, not exposed).
- `SavedSearchController.java` — `GET /api/v1/search/saved/{id}/export?limit=` → `text/csv` attachment (no class `@PreAuthorize`; owner check is in the service, consistent with the sibling saved-search reads). Filename `"<safeSearchName>-search-<yyyyMMdd-HHmmss>.csv"` (sanitized, id fallback), mirroring the version-CSV download response.

### Frontend
- `services/savedSearchService.ts` — `exportResultsCsv(id, name?)` → `api.downloadFile('/search/saved/{id}/export', '<safe>-search-<ts>.csv')`.
- `pages/SavedSearchesPage.tsx` — per-row "Export results CSV" `IconButton` (`FileDownload`) + `handleExportResults` (success/failure toast).

### Schema
- **None.** No migration, no scheduler, no async, no JSON export (all OOS per the brief).

## Tests added

- `SavedSearchServiceCsvExportTest.java` (new, 5) — header + rows + RFC-4180 escaping; **default limit caps page size at 1000**; **requested limit clamped to 5000** (both via `ArgumentCaptor` on the search request); non-owner → `SecurityException` (search never runs); missing search → `IllegalArgumentException`. Uses a real `ObjectMapper` for the `queryParams`→request conversion.
- `savedSearchService.test.ts` (+1) — `exportResultsCsv` calls `downloadFile` with the export URL + a sanitized dated filename (added `downloadFile` to the api mock).

## Local verification

```
savedSearchService.test.ts ........... 15/15 PASS (incl. the new export test)
SavedSearchesPage.test.tsx ........... 2/2 PASS (no regression from the new row button)
react-scripts build (CI=true) ........ success (ESLint clean)
```

Backend test (`SavedSearchServiceCsvExportTest`) ships via the Surefire glob and runs in CI (no local Docker/mvnw).

## Decisions / notes

- Caps applied exactly: default 1000, hard 5000 — locked from both sides in the service test.
- `SearchResult` is a top-level `com.ecm.core.search.SearchResult` (`@Data @Builder`), not nested in `FacetedSearchService` — imported accordingly (caught during implementation).
- Permission contract is trivial (owner-only, same as execute), so no permission adjudication was needed (unlike #1 bulk-share).
- `@RequiredArgsConstructor` arity check (per `feedback_requiredargsconstructor_arity_breaks_standalone_tests`): no field was added to `SavedSearchService`/`SavedSearchController` (only new methods + imports), so no manual-construction fixture breaks — grep for `new SavedSearchService(` / `new SavedSearchController(` confirmed nothing else constructs them with the old shape.

## CI Follow-Up

Round 1 (`323ac39`) failed Backend Verify on a single `testCompile` error: the test fixture used
`SearchResult.builder().id(UUID.randomUUID())`, but `SearchResult.id` is a `String` (verified the
field existed, not its type). Fixed in `b74c56f` (`test(core): align saved search CSV export
result id type` — `.id(UUID.randomUUID().toString())`). Frontend + Phase 5 passed in round 1;
only that line broke compilation. Lesson: when building a `@Builder` entity in a test, verify
field **types**, not just existence (id is `String` here, UUID elsewhere) — no local `mvnw` catches it.

```
Run id:        26400740974
Head SHA:      b74c56f9
Conclusion:    success (7/7 — gh run view authority per feedback_gh_run_watch_unreliable)
URL:           https://github.com/zensgit/Athena/actions/runs/26400740974

Jobs (7/7 green):
  ✓ Backend Verify
  ✓ Frontend Build & Test
  ✓ Phase C Security Verification
  ✓ Frontend E2E Core Gate
  ✓ Property Encryption Closeout Gate
  ✓ Phase 5 Mocked Regression Gate
  ✓ Acceptance Smoke (3 admin pages)
```
