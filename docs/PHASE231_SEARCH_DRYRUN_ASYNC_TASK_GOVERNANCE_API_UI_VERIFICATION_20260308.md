# Phase 231 - Search Dry-Run Async Task Governance API/UI - Verification

## Date
2026-03-08

## Scope
- 验证 Search dry-run async export 新增治理能力（status filter + summary + cleanup）在后端与前端的一致性。

## Commands and results

1. Backend controller security tests
```bash
cd ecm-core
mvn -q -Dtest=SearchControllerSecurityTest test
```
- Result: PASS

2. Frontend lint (targeted files)
```bash
cd ecm-frontend
npm run -s lint -- src/pages/AdvancedSearchPage.tsx src/services/nodeService.ts
```
- Result: PASS

3. Mocked E2E (Chromium)
```bash
cd ecm-frontend
ECM_UI_URL=http://localhost:5500 npx playwright test e2e/advanced-search-preview-batch-scope.mock.spec.ts --project=chromium
```
- Result: PASS

4. Cross-spec mocked E2E regression (Preview + Search)
```bash
cd ecm-frontend
ECM_UI_URL=http://localhost:5500 npx playwright test \
  e2e/admin-preview-diagnostics.mock.spec.ts \
  e2e/advanced-search-preview-batch-scope.mock.spec.ts \
  --project=chromium
```
- Result: PASS

## Verified outcomes
- Search async task list/summary/cleanup 全部按 status 过滤一致生效。
- cleanup 行为与后端治理约束一致（终态清理策略）。
- UI 与 mocked API 交互闭环通过回归。

