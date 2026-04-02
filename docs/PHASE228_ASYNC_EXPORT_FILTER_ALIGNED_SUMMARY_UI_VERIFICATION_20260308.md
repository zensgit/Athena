# Phase 228 - Async Export Filter-Aligned Summary UI - Verification

## Date
2026-03-08

## Scope
- 验证 Admin/Audit 与 PreviewDiagnostics/Ops 的 summary 过滤联动。
- 验证 mocked E2E 中 list/summary/cleanup 参数口径一致。

## Commands and results

1. Frontend lint (source files)
```bash
cd ecm-frontend
npm run -s lint -- src/pages/AdminDashboard.tsx src/pages/PreviewDiagnosticsPage.tsx src/services/opsRecoveryService.ts
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
  e2e/admin-audit-filter-export.mock.spec.ts \
  e2e/admin-preview-diagnostics.mock.spec.ts \
  --project=chromium
```
- Result: PASS

## Notes
- 对 e2e 文件执行 `npm run -s lint -- e2e/...` 仍会触发仓库内既有 `jest/testing-library` 规则报错（历史基线问题），本次未新增该类规则违规。

## Verified outcomes
- Audit summary 在切换 task status 过滤后按相同 status 返回统计。
- Ops summary 按 `exportType + status` 过滤返回统计。
- cleanup 参数与当前过滤器语义一致，E2E 断言通过。
