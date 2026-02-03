# Phase 14 E2E Stability (Dev) - 2026-02-03

## Goals
- Stabilize full E2E suite under auth bypass.
- Fix search sort failures when `sortBy` is supplied.
- Make scheduled-rule trigger resilient to concurrent updates.
- Harden E2E scheduled-rule validation for eventual tag propagation.

## Changes
### Backend
- Remove `_id` tie-breaker for explicit sorts to avoid ES fielddata errors; use `nameSort` as safe tie-breaker:
  - `ecm-core/src/main/java/com/ecm/core/search/FullTextSearchService.java`
- Treat optimistic lock conflicts during manual scheduled rule trigger as non-fatal (return 200 + warning):
  - `ecm-core/src/main/java/com/ecm/core/controller/RuleController.java`

### Frontend E2E
- Add auth bypass support to additional login helpers + role mapping for editor/viewer flows:
  - `ecm-frontend/e2e/browse-acl.spec.ts`
  - `ecm-frontend/e2e/permissions-dialog.spec.ts`
  - `ecm-frontend/e2e/rules-manual-backfill-validation.spec.ts`
  - `ecm-frontend/e2e/search-preview-status.spec.ts`
  - `ecm-frontend/e2e/search-sort-pagination.spec.ts`
  - `ecm-frontend/e2e/ui-smoke.spec.ts`
- Ensure skip-login flow navigates to `/browse/root` when needed:
  - `ecm-frontend/e2e/ui-smoke.spec.ts`
- Extend scheduled-rule tag polling window to absorb eventual consistency:
  - `ecm-frontend/e2e/ui-smoke.spec.ts`

### Tests
- Added `SecurityService` mock in `SearchControllerSecurityTest` to restore WebMvcTest context:
  - `ecm-core/src/test/java/com/ecm/core/controller/SearchControllerSecurityTest.java`

## Notes
- Search API now returns results when `sortBy` is provided (fixes prior empty responses and E2E failures).
- Scheduled rule manual trigger may still log an optimistic-lock warning if concurrent updates occur, but returns 200 and rules execute successfully.
