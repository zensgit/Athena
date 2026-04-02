# Phase 193 - Advanced Search All-Matched Preview Batch Actions (Verification)

## Date
2026-03-07

## Scope
- Verify advanced search supports all-matched failed preview batch actions.
- Verify UI changes compile and lint cleanly.
- Verify mock E2E covers request propagation and action behavior.

## Commands and results

1. Frontend lint (changed files)
```bash
cd ecm-frontend
npm run -s lint -- src/pages/AdvancedSearchPage.tsx e2e/advanced-search-preview-batch-scope.mock.spec.ts
```
- Result: PASS

2. Frontend production build
```bash
cd ecm-frontend
npm run -s build
```
- Result: PASS

3. Mocked Playwright E2E
```bash
cd ecm-frontend
ECM_UI_URL=http://localhost:3000 npm run -s e2e -- \
  e2e/advanced-search-preview-batch-scope.mock.spec.ts \
  --project=chromium --reporter=list
```
- Result: PASS (`1 passed`)

## Verified outcomes
- Advanced Search now exposes all-matched batch actions with safety cap (`max 200`).
- Reason-level all-matched retry/rebuild controls are available alongside existing current-page actions.
- All-matched workflow issues bounded search requests with failed-preview scope (`previewStatus=FAILED`) before queuing retries.
- Existing current-page retry button still queues preview successfully in mocked flow.
