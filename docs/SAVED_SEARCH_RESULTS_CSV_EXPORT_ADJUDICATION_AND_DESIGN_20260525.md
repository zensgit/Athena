# Saved-Search Results CSV Export - Adjudication and Design

Date: 2026-05-25
Status: read-only brief, awaiting gate
Scope type: product-capability slice

## 1. Context

`docs/PRODUCT_CAPABILITY_DISCOVERY_REFRESH3_20260525.md` recommends pausing product-capability autopicking. If a small low-risk slice is still desired, it ranks saved-search results CSV export as the least-weak candidate.

This brief keeps that honesty posture:

- There is no captured operator demand in the repo.
- This is a plausible view-but-not-export gap, not a requested workflow.
- The proposed slice is one-shot CSV export only.
- No scheduler, recurring delivery, saved-search sharing, saved-search import/export JSON changes, schema changes, or search engine changes are in scope.

## 2. Primary-source facts

### Existing saved-search backend surface

- `ecm-core/src/main/java/com/ecm/core/controller/SavedSearchController.java:18` maps `/api/v1/search/saved`.
- Existing routes:
  - save: `POST /api/v1/search/saved` (`SavedSearchController.java:25-30`)
  - list: `GET /api/v1/search/saved` (`:32-36`)
  - templates: `GET /api/v1/search/saved/templates` (`:38-52`)
  - get: `GET /api/v1/search/saved/{id}` (`:54-58`)
  - update: `PATCH /api/v1/search/saved/{id}` (`:60-65`)
  - delete: `DELETE /api/v1/search/saved/{id}` (`:67-72`)
  - execute: `GET /api/v1/search/saved/{id}/execute` (`:74-78`)
  - pin: `PATCH /api/v1/search/saved/{id}/pin` (`:80-84`)
  - create smart folder: `POST /api/v1/search/saved/{id}/smart-folder` (`:86-93`)
- There is no saved-search results export route.

### Existing saved-search execution semantics

- `ecm-core/src/main/java/com/ecm/core/service/SavedSearchService.java:239-257` implements `executeSavedSearch(UUID id)`.
- It loads the saved search for the current user and rejects foreign-user access with `SecurityException` (`:241-247`).
- It converts stored `queryParams` into `FacetedSearchRequest` via `ObjectMapper.convertValue(...)` (`:249-252`).
- It delegates to `FacetedSearchService.search(request)` (`:252`).

This means export must preserve saved-search ownership and should reuse the same query conversion path.

### Search result and pagination facts

- `FacetedSearchService.search(...)` builds a `Page<SearchResult>` and returns `FacetedSearchResponse` (`FacetedSearchService.java:98-144`).
- If `request.pageable` is null, the backend defaults to `PageRequest.of(0, 20)` (`FacetedSearchService.java:127-132` and `SimplePageRequest.java:12-18`).
- `SearchResult` has exportable fields such as `id`, `name`, `path`, `nodeType`, `mimeType`, `fileSize`, `currentVersionLabel`, creator/date, modifier/date, tags, categories, record metadata, and preview status (`SearchResult.java:21-51`).

Important consequence: if the export simply calls `executeSavedSearch(id)`, it will export only the saved search's stored page or the default 20 rows. A one-shot export needs an explicit bounded export page size.

### Existing CSV attachment pattern

- The just-shipped version-history CSV endpoint uses `GET /api/v1/documents/{documentId}/versions/export` (`DocumentController.java:213-240`).
- It returns `ResponseEntity<String>` with `Content-Disposition: attachment` and `text/csv` (`DocumentController.java:235-240`).
- CSV escaping is local and RFC-4180-style (`VersionService.java:244-260`).
- Frontend uses `api.downloadFile(...)` for download flows (`ecm-frontend/src/services/api.ts:356-367`) and `nodeService.exportVersionHistoryCsv(...)` as a direct precedent (`nodeService.ts:3373-3387`).

### Existing saved-search frontend surface

- `ecm-frontend/src/services/savedSearchService.ts:255-310` owns saved-search API methods.
- `savedSearchService.execute(id)` calls `GET /search/saved/{id}/execute` and guards the `FacetedSearchResponse` (`:301-304`).
- `SavedSearchesPage.tsx:124-132` runs a saved search and navigates to search results.
- `SavedSearchesPage.tsx:193-201` copies a saved-search link.
- `SavedSearchesPage.tsx:255-272` exports saved-search definitions as JSON. That is configuration import/export, not result export.
- The action column in `SavedSearchesPage.tsx:441-496` has room for one more row action, though width may need a small adjustment.

## 3. Adjudication

### Is this already built?

No. Audit-history export was already built and removed as a candidate in refresh3, but saved-search result export is not present.

Existing JSON export in `SavedSearchesPage` exports the saved-search definitions, not the search results. It should not be renamed or repurposed in this slice.

### Is this worth doing?

Weak yes, if the gate wants one more small low-risk product slice despite no direct operator signal.

Reasons:

- It is read-only.
- It reuses a freshly verified CSV pattern.
- It gives a concrete missing affordance on an existing page.
- It does not touch scheduler, schema, permissions, or result mutation paths.

Reasons to pause instead:

- The product-value signal is inferred.
- More CSV buttons can become low-value polish if not tied to operator evidence.

## 4. Proposed backend design

### Endpoint

Add:

```java
@GetMapping(value = "/{id}/export", produces = "text/csv")
public ResponseEntity<String> exportSavedSearchResults(
    @PathVariable UUID id,
    @RequestParam(defaultValue = "1000") int limit
)
```

Route:

`GET /api/v1/search/saved/{id}/export?limit=1000`

Recommended max:

- Default `limit=1000`.
- Clamp to `1..5000`.

Reasoning:

- Saved searches can match large result sets.
- A one-shot synchronous export should not become an unbounded Elasticsearch scan.
- If operators later need full large-result exports, that is an async export task track.

### Service method

Add to `SavedSearchService`:

```java
@Transactional(readOnly = true)
public String exportSavedSearchResultsCsv(UUID id, int limit)
```

Implementation shape:

1. Load and authorize the saved search exactly like `getMySavedSearch` / `executeSavedSearch`.
2. Convert `queryParams` to `FacetedSearchRequest`.
3. Override `request.pageable` to `page=0`, `size=boundedLimit`.
4. Call `facetedSearchService.search(request)`.
5. Build CSV from `response.getResults().getContent()`.

Do not reuse `executeSavedSearch(id)` directly unless it is refactored to accept an explicit export pageable. The current no-arg method preserves normal execute semantics and may default to 20 rows.

### CSV columns

Recommended v1 columns:

1. `ID`
2. `Name`
3. `Path`
4. `Node Type`
5. `MIME Type`
6. `Size (bytes)`
7. `Current Version`
8. `Created By`
9. `Created Date`
10. `Modified By`
11. `Modified Date`
12. `Tags`
13. `Categories`
14. `Correspondent`
15. `Record`
16. `Record Category`
17. `Preview Status`
18. `Preview Failure Category`
19. `Preview Failure Reason`

Reasoning:

- These fields are already present on `SearchResult`.
- They cover document identity, location, metadata, RM status, and preview health.
- They avoid highlights/facets because those are UI/search-explanation artifacts, not stable CSV columns.

### CSV escaping

Use a local helper, not a cross-controller import:

```java
private static String csvEscape(Object value) { ... }
private static void appendCsvRow(StringBuilder target, Object... values) { ... }
```

The behavior should match `VersionService.csvEscape`: null -> blank; quote values containing comma, quote, CR, or LF; double embedded quotes.

### Filename

Recommended backend filename:

`<safeSavedSearchName>-results-<yyyyMMdd-HHmmss>.csv`

Rules:

- Use saved search name when nonblank.
- Sanitize to `[A-Za-z0-9._-]`, replacing other characters with `_`.
- Fall back to saved-search ID if sanitized name is blank.
- Include timestamp.

The frontend may provide the same local default filename to `api.downloadFile`, but the backend `Content-Disposition` should still be correct.

### Security

The route should require authentication through existing security configuration, like other saved-search routes.

Ownership/authorization is enforced in `SavedSearchService`:

- Saved search missing -> same not-found behavior as existing saved-search service.
- Saved search owned by another user -> `SecurityException`.

No admin override in v1.

## 5. Proposed frontend design

### Service

Extend `ecm-frontend/src/services/savedSearchService.ts`:

```ts
async exportResultsCsv(item: SavedSearch, limit = 1000): Promise<void> {
  const safeName = ...
  return api.downloadFile(
    `/search/saved/${item.id}/export`,
    `${safeName}-results-${timestamp}.csv`,
    { params: { limit } },
  );
}
```

This is a blob/download method. It should not add a JSON response guard.

### UI

Add an action icon to `SavedSearchesPage` row actions:

- Label: `Export results CSV`
- Suggested icon: existing `FileDownload`
- On click: call `savedSearchService.exportResultsCsv(item)`
- Toast:
  - success: `Exported saved search results`
  - failure: `Failed to export saved search results`

Keep the existing top-level `Export JSON` button unchanged. It exports saved-search definitions and is not this feature.

### Limit UI

Recommended v1:

- No new dialog and no visible limit input.
- Use default `limit=1000`.
- If gate wants transparency, add tooltip/copy: `Exports up to 1,000 matching results`.

Reasoning:

- This keeps the slice below 1 day.
- Large exports should be a separate async/scheduler track.

## 6. Tests

### Backend service tests

Add or extend a saved-search service test:

- `exportSavedSearchResultsCsv` authorizes current user.
- Foreign-user saved search throws `SecurityException`.
- Missing saved search throws the existing not-found exception shape.
- Converts stored query params and overrides pageable to `page=0`, bounded `size`.
- Emits header + one row with RFC-4180 escaping.
- Null fields become blank.
- Tags/categories lists are joined deterministically, e.g. `; `.
- Limit is clamped to `1..5000`.

### Backend controller tests

Add to `SavedSearchController*Test` or a focused new class:

- `GET /api/v1/search/saved/{id}/export` returns `text/csv`.
- `Content-Disposition` is `attachment` and filename contains sanitized saved-search name.
- `limit` is passed to service.
- Not-found/security errors keep existing handler behavior.

If adding a constructor dependency or changing `@RequiredArgsConstructor` fields, grep for manual construction tests:

```bash
rg "new SavedSearchController\\(" ecm-core/src/test/java
rg "new SavedSearchService\\(" ecm-core/src/test/java
```

This prevents repeating the `@RequiredArgsConstructor` standalone-test arity issue from the bulk share-link slice.

### Frontend service tests

Extend `savedSearchService.test.ts`:

- `exportResultsCsv` calls `api.downloadFile('/search/saved/saved-1/export', <filename>, { params: { limit: 1000 } })`.
- Filename is sanitized and timestamped.
- Custom limit is forwarded if exposed in service signature.

The current mock for `api` lacks `downloadFile`; update it in the test file only.

### Frontend page tests

Extend `SavedSearchesPage.test.tsx`:

- Row action appears with accessible label `Export results CSV <name>`.
- Clicking it calls `savedSearchService.exportResultsCsv(item)`.
- Success toast fires.
- Failure toast fires.

The existing DataGrid mock renders action-cell buttons, so this should stay local to `SavedSearchesPage.test.tsx`.

## 7. Gate decisions needed

D1. Endpoint:

Recommended: `GET /api/v1/search/saved/{id}/export`.

D2. Export size:

Recommended: synchronous one-shot export with `limit` default 1000 and max 5000. No unbounded export.

D3. Columns:

Recommended: the 19-column set in §4, excluding highlights/facets.

D4. UI entry:

Recommended: row action in `SavedSearchesPage`, not top-level export button. Keep `Export JSON` as definition export.

D5. Filename:

Recommended: `<safeSavedSearchName>-results-<yyyyMMdd-HHmmss>.csv`, saved-search ID fallback.

D6. Large/scheduled exports:

Recommended: explicitly out of scope. If users need full exports above 5000 or recurring delivery, open an async export/scheduler track.

## 8. Out of scope

- Recurring/scheduled saved-search export.
- Async export task registry.
- Exporting all results without a cap.
- Modifying search ranking, filters, ACL behavior, or facets.
- Saved-search definition JSON import/export changes.
- Saved-search sharing or public links.
- Smart-folder changes.
- Search result schema changes.
- New database schema or migrations.
- `.env`, YAML, docker-compose, Logback, or unrelated RM/mail/share-link code.

## 9. Verification plan for implementation slice

Local targeted checks:

- `cd ecm-core && ./mvnw -Dtest=SavedSearchService*Test,SavedSearchController*Test test` if local Docker/Maven environment allows.
- `cd ecm-frontend && npm test -- --watchAll=false src/services/savedSearchService.test.ts src/pages/SavedSearchesPage.test.tsx`
- `cd ecm-frontend && npm run lint`
- `cd ecm-frontend && CI=true npm run build`
- `git diff --check -- . ':!.env'`

CI:

- Push code/test commit plus verification doc.
- Use `gh run view` conclusion as the authoritative gate.
- If CI fails, diagnose one concrete root cause and fix narrowly.
- After 7/7 green, append CI follow-up and commit `[skip ci]`.

## 10. Recommended cadence

1. `feat(core): export saved-search results as CSV`
   - backend endpoint/service + frontend service/page + tests.
2. `docs(core): record saved-search results CSV export verification`
   - this brief plus verification doc.
3. Optional `test(core): ...`
   - only for CI-exposed fixture/contract alignment.
4. `docs(core): record CI for saved-search results CSV export [skip ci]`
   - only after final 7/7 success.

