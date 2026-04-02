# Phase 218 - Ops Recovery Async Export Task Filter by Type - Verification

## Date
2026-03-08

## Scope
- 验证后端 `exportType` 列表过滤能力与安全测试通过。
- 验证前端筛选控件、服务参数透传与 mocked E2E 回归。

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
- 后端 list API 支持 `exportType` 过滤，且管理端安全约束不回退。
- 页面任务中心可切换任务类型过滤，并驱动服务端按类型返回列表。
- mocked E2E 已覆盖筛选参数传递和筛选后任务列表行为。
