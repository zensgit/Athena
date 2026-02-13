# Phase 1 P87 Verification: Advanced Search Aspects/Custom Properties Saved-Search Parity

## Verification Date
- 2026-02-12 (CST)

## Code Validation
1. ESLint (targeted)
- Command:
```bash
cd ecm-frontend && npx eslint \
  src/store/slices/uiSlice.ts \
  src/utils/savedSearchUtils.ts \
  src/utils/savedSearchUtils.test.ts \
  src/components/search/SearchDialog.tsx \
  src/pages/SavedSearchesPage.tsx \
  src/services/nodeService.ts \
  e2e/saved-search-load-prefill.spec.ts \
  e2e/search-dialog-preview-status.spec.ts
```
- Result: pass

2. Unit test (saved-search mapper)
- Command:
```bash
cd ecm-frontend && CI=true npm test -- --watch=false src/utils/savedSearchUtils.test.ts
```
- Result: pass (`1 passed`)

## E2E Validation (Playwright)
1. Saved-search prefill + save payload regression set
- Command:
```bash
cd ecm-frontend && ECM_UI_URL=http://localhost:3000 \
  npx playwright test \
  e2e/saved-search-load-prefill.spec.ts \
  e2e/search-dialog-preview-status.spec.ts \
  --reporter=list
```
- Result: pass (`3 passed`)
- Covered assertions:
  - Load saved search restores:
    - preview statuses
    - folder scope
    - aspects (`Versionable`, `Taggable`)
    - custom property chips (`mail:subject`, `mail:uid`)
  - Save from dialog includes:
    - `queryParams.filters.aspects`
    - `queryParams.filters.properties`

## Outcome
- P87 scope verified complete:
  - Advanced Search saved-search flow now preserves and restores `Aspects` + `Custom Properties` consistently.

