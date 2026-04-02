# Phase 226 - Ops Recovery Async Task Status Filter UI - Verification

## Date
2026-03-08

## Scope
- 验证 ops recovery async task status filter 的前端接入与 mocked E2E 行为。
- 验证 list/cleanup 请求参数包含 `status`。

## Commands and results

1. Frontend lint
```bash
cd ecm-frontend
npm run -s lint -- src/services/opsRecoveryService.ts src/pages/PreviewDiagnosticsPage.tsx
npm run -s lint -- e2e/admin-preview-diagnostics.mock.spec.ts
```
- Result: PASS

2. Frontend build
```bash
cd ecm-frontend
npm run -s build
```
- Result: PASS

3. Mocked E2E
```bash
cd ecm-frontend
ECM_UI_URL=http://localhost:5500 npx playwright test \
  e2e/admin-audit-filter-export.mock.spec.ts \
  e2e/admin-preview-diagnostics.mock.spec.ts \
  --project=chromium
```
- Result: PASS

## Verified outcomes
- 列表可按 status 过滤，且与 exportType 组合工作。
- cleanup 在 terminal status 选中时会携带 `status` 参数。
- mocked E2E 已覆盖 `status=COMPLETED` 的 list/cleanup 请求断言。
