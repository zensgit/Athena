# Phase 216 - Preview Rendition Resources Async Export Task Center - Verification

## Date
2026-03-08

## Scope
- 验证后端异步导出任务接口安全性与可编译性。
- 验证前端任务中心 UI、服务调用与 mocked E2E 回归稳定性。

## Commands and results

1. Backend security test
```bash
cd ecm-core
mvn -q -Dtest=PreviewDiagnosticsControllerSecurityTest test
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
  src/services/previewDiagnosticsService.ts \
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
ECM_UI_URL=http://localhost:5601 npx playwright test \
  e2e/admin-preview-diagnostics.mock.spec.ts \
  e2e/advanced-search-preview-batch-scope.mock.spec.ts \
  --project=chromium
```
- Result: PASS (`3 passed`)

## Verified outcomes
- Async task center backend endpoints are admin-protected and reachable under expected status contracts.
- Preview Diagnostics UI can start async export, refresh task list, cancel queued/running task, and download completed CSV.
- Existing advanced-search preview batch mocked flows remain green after this change set.
