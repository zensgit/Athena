# Phase 222 - Audit Async Task Governance UI - Verification

## Date
2026-03-08

## Scope
- 验证 Admin Dashboard 对 summary/cleanup API 的前端集成。
- 验证 mocked E2E 任务中心治理流程。

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

3. Mocked Playwright E2E - audit task center
```bash
cd ecm-frontend
ECM_UI_URL=http://localhost:5500 npx playwright test \
  e2e/admin-audit-filter-export.mock.spec.ts \
  --project=chromium
```
- Result: PASS (`1 passed`)

4. Mocked Playwright regression - preview diagnostics
```bash
cd ecm-frontend
ECM_UI_URL=http://localhost:5500 npx playwright test \
  e2e/admin-preview-diagnostics.mock.spec.ts \
  --project=chromium
```
- Result: PASS (`1 passed`)

## Verified outcomes
- 任务中心摘要与清理操作可正常展示与执行。
- 在 `Completed` 筛选下清理动作会携带 `status=COMPLETED`。
- 同域诊断页面 mocked 回归无回退。
