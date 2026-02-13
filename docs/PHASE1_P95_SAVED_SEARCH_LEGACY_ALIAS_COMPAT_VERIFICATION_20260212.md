# Phase 1 P95 Verification: Saved Search Legacy Alias Compatibility

## Verification Date
- 2026-02-12 (local)

## Validation Steps
1. Lint
- Command:
```bash
cd ecm-frontend && npx eslint \
  src/utils/savedSearchUtils.ts \
  src/utils/savedSearchUtils.test.ts \
  e2e/saved-search-load-prefill.spec.ts
```
- Result: pass

2. Unit tests
- Command:
```bash
cd ecm-frontend && CI=true npm test -- --watch=false src/utils/savedSearchUtils.test.ts
```
- Result: pass (`3 passed`)

3. Playwright regression (current branch code on local dev server)
- Command:
```bash
cd ecm-frontend && ECM_UI_URL=http://localhost:3000 \
  npx playwright test \
  e2e/saved-search-load-prefill.spec.ts \
  e2e/search-dialog-active-criteria-summary.spec.ts \
  e2e/search-dialog-preview-status.spec.ts \
  --reporter=list
```
- Result: pass (`10 passed`)

## Notes
- A failed run against `http://localhost:5500` was environment drift (stale deployed UI), not code regression.
- Final pass was validated against local `npm start` server on `http://localhost:3000`.

## Outcome
- Legacy alias fields now map correctly into advanced-search prefill:
  - `pathPrefix` -> `path`
  - `createdFrom/createdTo` -> created date window
  - `previewStatus` string -> `previewStatuses[]`
  - `creators` -> `createdByList`/`createdBy`
- Existing saved-search dialog and preview-status flows remain green.
