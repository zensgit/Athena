# Phase 235 - Preview Async Task Center Mock E2E Auto-Start Gate - Verification

## Date
2026-03-09

## Scope
- 验证新增一键 mocked e2e 启动脚本可用性。
- 复核 Preview/Advanced Search async task-center 相关改动的后端与前端回归。

## Commands and results

1. Backend security regression (Preview diagnostics)
```bash
cd ecm-core
mvn -q -Dtest=PreviewDiagnosticsControllerSecurityTest test
```
- Result: PASS

2. Backend compile check
```bash
cd ecm-core
mvn -q -DskipTests compile
```
- Result: PASS

3. Backend security + controller regression (Search)
```bash
cd ecm-core
mvn -q -Dtest=SearchControllerSecurityTest,SearchControllerTest test
```
- Result: PASS

4. Frontend lint (targeted)
```bash
cd ecm-frontend
npm run -s lint -- \
  src/services/previewDiagnosticsService.ts \
  src/pages/PreviewDiagnosticsPage.tsx \
  e2e/admin-preview-diagnostics.mock.spec.ts \
  src/pages/AdvancedSearchPage.tsx \
  src/services/nodeService.ts \
  src/store/slices/nodeSlice.ts
```
- Result: PASS

5. Frontend build
```bash
cd ecm-frontend
npm run -s build
```
- Result: PASS

6. New one-command mocked e2e gate
```bash
cd /Users/huazhou/Downloads/Github/Athena
bash scripts/phase235-preview-async-task-center-mock-e2e.sh
```
- Result: PASS (`3 passed`)

## Verified outcomes
- 新脚本可自动拉起前端并执行 mocked Playwright，用后自动清理进程。
- Preview Diagnostics async task-center mocked flow稳定通过：
  - start / list / summary / cancel / download / cleanup / cancel-active
- Advanced Search preview batch async dry-run导出治理 mocked flow保持通过，无回归。
