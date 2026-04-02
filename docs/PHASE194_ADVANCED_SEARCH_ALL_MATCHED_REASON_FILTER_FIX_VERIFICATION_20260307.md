# Phase 194 - Advanced Search All-Matched Reason Filter Fix (Verification)

## Date
2026-03-07

## Scope
- Verify all-matched bulk retry no longer drops candidates when reason is not provided.
- Verify build/lint and mocked E2E pass after the fix.

## Commands and results

1. Frontend lint
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
- Current-page retry action still queues previews.
- All-matched retry action now triggers failed-scope scan and queues matched retryable targets.
- Reason-specific and reason-agnostic all-matched flows both operate correctly.
