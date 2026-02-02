# Phase 10 – Search Consistency & Facet Clarity

## Scope
- Improve search sort stability for consistent pagination.
- Clarify facet count scope and preview-status filtering behavior.
- Extend E2E to validate new search UI messaging.

## Changes
### Backend
- **Stable sorting with tie‑breaker**
  - Applied `_score` + `_id` for relevance and `_id` as a secondary tie‑breaker for other sort modes.
  - File: `ecm-core/src/main/java/com/ecm/core/search/FullTextSearchService.java`.

### Frontend
- **Facet scope messaging + filter behavior**
  - Facet counts now derived from current page results when non‑preview filters are active.
  - Added scope message indicating whether counts are from full query or current page.
  - File: `ecm-frontend/src/pages/SearchResults.tsx`.
- **Preview status filter messaging**
  - Added caption and results summary note explaining preview status filters apply per page.
  - File: `ecm-frontend/src/pages/SearchResults.tsx`.

### Tests
- Updated E2E to assert preview-status page-scope message.
  - File: `ecm-frontend/e2e/search-preview-status.spec.ts`.

## Notes
- Changes are UI and search sort stability; no API contract changes.
