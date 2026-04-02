# Phase 232 - Cancel-Active Governance UI Integration - Verification

## Date
2026-03-08

## Scope
- 验证 Audit/Ops 页面已接入 `cancel-active` 批量治理动作。
- 验证三条 mocked E2E 主链路无回归（Admin Audit、Preview Diagnostics、Advanced Search）。

## Commands and results

1. Frontend lint (source files)
```bash
cd ecm-frontend
npm run -s lint -- \
  src/pages/AdminDashboard.tsx \
  src/pages/PreviewDiagnosticsPage.tsx \
  src/services/opsRecoveryService.ts \
  src/services/previewDiagnosticsService.ts \
  src/pages/AdvancedSearchPage.tsx \
  src/services/nodeService.ts
```
- Result: PASS

2. Frontend build
```bash
cd ecm-frontend
npm run -s build
```
- Result: PASS

3. Mocked E2E regression pack (Chromium)
```bash
cd ecm-frontend
ECM_UI_URL=http://localhost:5500 npx playwright test \
  e2e/admin-audit-filter-export.mock.spec.ts \
  e2e/admin-preview-diagnostics.mock.spec.ts \
  e2e/advanced-search-preview-batch-scope.mock.spec.ts \
  --project=chromium
```
- Result: PASS (4 passed)

## Notes
- 对 `e2e/admin-audit-filter-export.mock.spec.ts` 单独执行 ESLint 仍会触发仓库既有 `jest/testing-library` 规则基线报错，本次未新增该类问题。

## Verified outcomes
- Audit 页面可触发 `cancel-active`，并按当前 active 状态过滤透传参数。
- Ops 页面可触发 `cancel-active`，并按当前 `exportType + active status` 透传参数。
- Preview Diagnostics + Advanced Search mocked 回归链路保持通过。

