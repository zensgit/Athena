# Phase 224 - Ops Recovery Async Task Governance UI - Verification

## Date
2026-03-08

## Scope
- 验证 Preview Diagnostics 中 ops recovery async summary/cleanup UI 接入。
- 验证 mocked E2E 覆盖 summary/cleanup 交互闭环。

## Commands and results

1. Frontend lint (targeted)
```bash
cd ecm-frontend
npm run -s lint -- src/pages/PreviewDiagnosticsPage.tsx
npm run -s lint -- e2e/admin-preview-diagnostics.mock.spec.ts
```
- Result: PASS

2. Frontend build
```bash
cd ecm-frontend
npm run -s build
```
- Result: PASS

3. Mocked E2E (Chromium)
```bash
cd ecm-frontend
ECM_UI_URL=http://localhost:5500 npx playwright test \
  e2e/admin-preview-diagnostics.mock.spec.ts \
  e2e/admin-audit-filter-export.mock.spec.ts \
  --project=chromium
```
- Result: PASS

## Verified outcomes
- 页面可展示 ops recovery async summary 计数并支持手动刷新。
- cleanup 操作可触发后端并反馈删除结果（success/info toast）。
- mocked E2E 已覆盖 summary/cleanup 路由调用与关键 UI 反馈。
