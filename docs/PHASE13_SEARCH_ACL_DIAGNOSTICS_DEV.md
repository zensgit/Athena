# Phase 13 - Search ACL Diagnostics & Regression Guard

## Summary
- Added a search diagnostics endpoint to expose ACL filter context for the current user.
- Surfaced access scope/ACL status in Search Results UI with quick refresh.
- Hardened search-view E2E to allow auth bypass for more reliable runs.

## Backend Changes
- New endpoint: `GET /api/v1/search/diagnostics`
  - Fields: username, admin flag, readFilterApplied, authorityCount, authoritySample, note, generatedAt.

## Frontend Changes
- Search Results UI now displays an "Access scope" panel with:
  - Admin vs Restricted scope
  - ACL filter state
  - Authority count and sample authorities
  - Note and updated timestamp
- Search view E2E supports token-based login bypass (`ECM_E2E_SKIP_LOGIN=1`).

## Files Changed
- `ecm-core/src/main/java/com/ecm/core/controller/SearchController.java`
- `ecm-frontend/src/services/nodeService.ts`
- `ecm-frontend/src/pages/SearchResults.tsx`
- `ecm-frontend/e2e/search-view.spec.ts`

