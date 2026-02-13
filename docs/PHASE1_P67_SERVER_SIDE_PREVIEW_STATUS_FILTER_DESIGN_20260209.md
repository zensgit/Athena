# Phase 1 P67 - Server-Side Preview Status Filtering (Design) - 2026-02-09

## Summary

The Search Results and Advanced Search pages expose a **Preview Status** filter (Ready/Processing/Failed/Unsupported/Pending).
Historically this filter was applied **client-side on the current page only**, which caused:

- Confusing totals/pagination (filters did not apply across pages).
- UX hints like "Preview status filters apply to the current page only."

P67 moves Preview Status filtering **server-side** so the filter applies to the **entire result set** and pagination totals remain correct.

## Goals

- Preview Status chips filter results **across all pages** (server-side).
- Totals/pagination reflect the filtered result set (no per-page caveat text).
- Maintain the existing UI semantics:
  - Preview status filtering applies to **documents** (folders are excluded).
  - `PENDING` means "previewStatus missing in the search index".
  - `UNSUPPORTED` includes stale/legacy index rows where status is `FAILED` but signals indicate unsupported.
  - `FAILED` excludes those stale unsupported cases (matches UI effective status mapping).

## Non-Goals

- Add preview status as a true facet aggregation (counts over the full result set). Counts shown on chips remain derived from the current page for now.
- Introduce a new persisted `QUEUED` status in the backend (UI still includes `QUEUED`, but the backend filter is based on indexed `previewStatus`).

## API / Interface Changes

### 1) Full-text search endpoint

`GET /api/v1/search` now accepts an **optional** query parameter:

- `previewStatus` (CSV string), example: `previewStatus=UNSUPPORTED` or `previewStatus=FAILED,UNSUPPORTED`

No change is required for existing clients.

### 2) Advanced / faceted search endpoints

Both JSON filter DTOs now accept an **optional** field:

- `filters.previewStatuses: string[]`

This reuses the existing `SearchFilters` model.

## Implementation Details

### Backend (ecm-core)

1. Extend `SearchFilters` to include `previewStatuses`.
2. Apply preview status filtering in Elasticsearch queries for:
   - `FullTextSearchService` (GET `/search` and POST `/search/advanced`)
   - `FacetedSearchService` (POST `/search/faceted`)
3. Centralize the query construction logic in `PreviewStatusFilterHelper` so both services match behavior.

Filtering rules (effective status):

- Always add `nodeType=DOCUMENT` filter when previewStatuses are provided.
- `PENDING`:
  - Matches documents where `previewStatus` is missing in the index (`must_not exists(previewStatus)`).
- `UNSUPPORTED`:
  - Matches `previewStatus=UNSUPPORTED`, OR
  - Matches `previewStatus=FAILED` when unsupported signals exist:
    - mimeType is one of `application/octet-stream`, `binary/octet-stream`, `application/x-empty`
    - OR previewFailureReason contains phrases aligned with `PreviewFailureClassifier` unsupported detection.
- `FAILED`:
  - Matches `previewStatus=FAILED` AND excludes the same unsupported signals.
- Other values:
  - Term query on `previewStatus` (case-insensitive on input; stored values are uppercase enum names).

### Frontend (ecm-frontend)

1. Add `previewStatuses?: string[]` to `SearchCriteria` and propagate to the backend via:
   - `GET /search?previewStatus=...` when only scope + previewStatuses are present (fast path)
   - `POST /search/advanced` when other advanced filters are present
2. Search Results page:
   - Preview Status chips now trigger a backend search (via stored criteria).
   - Remove per-page caveat text.
   - Totals always render as `Showing X of Y results`.
3. Advanced Search page:
   - Include `filters.previewStatuses` in the faceted search payload.
   - Clicking Preview Status chips re-runs the faceted search (page reset to 1).
   - Remove per-page caveat text and client-side page-only filtering.

## Testing Strategy

- Backend:
  - Extend `SearchAclElasticsearchTest` to cover preview status filtering (PENDING/FAILED/UNSUPPORTED and stale unsupported-as-failed behavior).
- Frontend:
  - Update Playwright coverage to assert:
    - Search Results preview status chips trigger a server-side request including `previewStatus=...`
    - Totals update to the filtered set (`Showing 1 of 1 results` for a 2-item set after filtering)
    - Advanced Search preserves `previewStatus=UNSUPPORTED` in URL and continues to hide retry actions for unsupported previews.

## Risks / Mitigations

- Risk: Elasticsearch mapping / data drift (some indexed docs missing `nodeType` or preview fields).
  - Mitigation: filter semantics are only engaged when previewStatuses are provided; production indexing should always include `nodeType`.
- Risk: false-positive unsupported signal matching on `previewFailureReason`.
  - Mitigation: keep reason phrases aligned with backend classifier (narrow set) and always require `previewStatus=FAILED` for the stale unsupported path.

