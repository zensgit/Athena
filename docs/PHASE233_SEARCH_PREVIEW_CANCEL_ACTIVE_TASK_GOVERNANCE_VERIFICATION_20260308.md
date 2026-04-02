# Phase 233 - Search + Preview Cancel-Active Task Governance - Verification

## Date
2026-03-08

## Scope
- 验证 Search dry-run 与 Preview rendition 两条 async export 新增 `cancel-active` 能力在后端、前端、mocked e2e 的一致性。

## Commands and results

1. Backend targeted tests
```bash
cd ecm-core
mvn -q -Dtest=SearchControllerTest,SearchControllerSecurityTest,PreviewDiagnosticsControllerSecurityTest test
```
- Result: PASS

2. Backend compile
```bash
cd ecm-core
mvn -q -DskipTests compile
```
- Result: PASS

3. Frontend lint (targeted files)
```bash
cd ecm-frontend
npm run -s lint -- \
  src/services/nodeService.ts \
  src/services/previewDiagnosticsService.ts \
  src/pages/AdvancedSearchPage.tsx \
  src/pages/PreviewDiagnosticsPage.tsx \
  e2e/advanced-search-preview-batch-scope.mock.spec.ts \
  e2e/admin-preview-diagnostics.mock.spec.ts
```
- Result: PASS

4. Frontend build
```bash
cd ecm-frontend
npm run -s build
```
- Result: PASS

5. Mocked E2E regression (Chromium)
```bash
cd ecm-frontend
ECM_UI_URL=http://localhost:5500 npx playwright test \
  e2e/advanced-search-preview-batch-scope.mock.spec.ts \
  e2e/admin-preview-diagnostics.mock.spec.ts \
  --project=chromium
```
- Result: PASS (3 passed)

## Verified outcomes
- Search `cancel-active` 支持：
  - default active cancel
  - active-status filter (`QUEUED`/`RUNNING`)
  - terminal status rejection (`400`)
- Preview rendition `cancel-active` 支持：
  - default active cancel
  - active-status filter (`QUEUED`/`RUNNING`)
  - terminal status rejection (`400`)
- 前端任务中心对两条链路均已接入批量取消活动任务动作，且按当前过滤条件透传。
