# Phase 217 - Ops Recovery History Async Export Task Center - Verification

## Date
2026-03-08

## Scope
- 验证 Ops Recovery Async Export Task Center 后端安全性与编译通过。
- 验证前端任务中心交互与 mocked E2E 稳定性。

## Commands and results

1. Backend security test
```bash
cd ecm-core
mvn -q -Dtest=OpsRecoveryControllerSecurityTest test
```
- Result: PASS

2. Backend compile
```bash
cd ecm-core
mvn -q -DskipTests compile
```
- Result: PASS

3. Frontend lint (targeted)
```bash
cd ecm-frontend
npm run -s lint -- \
  src/services/opsRecoveryService.ts \
  src/pages/PreviewDiagnosticsPage.tsx \
  e2e/admin-preview-diagnostics.mock.spec.ts
```
- Result: PASS

4. Frontend build
```bash
cd ecm-frontend
npm run -s build
```
- Result: PASS

5. Mocked Playwright E2E
```bash
cd ecm-frontend
npx serve -s build -l 5500
ECM_UI_URL=http://localhost:5500 npx playwright test \
  e2e/admin-preview-diagnostics.mock.spec.ts \
  e2e/advanced-search-preview-batch-scope.mock.spec.ts \
  --project=chromium
```
- Result: PASS (`3 passed`)

## Verified outcomes
- Ops recovery async export endpoints are admin-protected and satisfy start/list/status/cancel/download contracts.
- Preview Diagnostics 页面可正常启动异步导出任务，并执行取消与下载动作。
- mocked 回归覆盖可稳定复现 task-center 主流程。
