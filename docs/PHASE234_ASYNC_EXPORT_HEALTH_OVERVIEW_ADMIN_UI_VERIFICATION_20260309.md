# Phase 234 - Async Export Health Overview (Admin UI) - Verification

## Date
2026-03-09

## Scope
- 验证 Admin Dashboard 新增四域异步导出健康总览的渲染、聚合、刷新与回归稳定性。

## Commands and results

1. Frontend lint (targeted)
```bash
cd ecm-frontend
npm run -s lint -- src/pages/AdminDashboard.tsx
```
- Result: PASS

2. Frontend build
```bash
cd ecm-frontend
npm run -s build
```
- Result: PASS

3. Mocked E2E regression (Chromium)
```bash
cd ecm-frontend
ECM_UI_URL=http://localhost:5601 npx playwright test \
  e2e/admin-audit-filter-export.mock.spec.ts \
  e2e/advanced-search-preview-batch-scope.mock.spec.ts \
  e2e/admin-preview-diagnostics.mock.spec.ts \
  --project=chromium
```
- Result: PASS (4 passed)

## Verified outcomes
- Admin Dashboard displays `Async Export Health Overview` card with:
  - four domains: Audit / Ops Recovery / Search / Preview
  - aggregate counters: `Total`, `Active`, `Terminal`, `Completed`, `Failed`, `Cancelled`
- Overview refresh action triggers re-fetch of cross-domain summary endpoints.
- Existing async governance flows remained valid in mocked e2e:
  - audit async export start/list/summary/cancel/download/cleanup/cancel-active
  - search preview batch dry-run async export governance
  - preview diagnostics async export governance
