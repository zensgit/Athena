# Design: E2E Shared API Helpers (2026-01-09)

## Goal
- Reduce duplicated API polling and auth logic across E2E specs.

## Approach
- Add `ecm-frontend/e2e/helpers/api.ts` with shared helpers for API readiness, Keycloak token fetch, search indexing waits, and folder/document lookups.
- Replace local helper implementations in the affected specs with imports from the shared helper file.
- Keep helper options configurable (base URL, timeouts, page size) to preserve existing test behavior.

## Files
- `ecm-frontend/e2e/helpers/api.ts`
- `ecm-frontend/e2e/ui-smoke.spec.ts`
- `ecm-frontend/e2e/pdf-preview.spec.ts`
- `ecm-frontend/e2e/search-view.spec.ts`
- `ecm-frontend/e2e/search-sort-pagination.spec.ts`
- `ecm-frontend/e2e/version-details.spec.ts`
- `ecm-frontend/e2e/version-share-download.spec.ts`
