# Phase 220 - Audit Export Async Task Status Filter - Verification

## Date
2026-03-08

## Scope
- 验证后端 `status` 过滤能力与错误输入保护。
- 验证前端状态筛选器与 mocked E2E 的参数透传和行为一致性。

## Commands and results

1. Backend controller tests
```bash
cd ecm-core
mvn -q -Dtest=AnalyticsControllerTest test
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
npm run -s lint -- src/pages/AdminDashboard.tsx
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
ECM_UI_URL=http://localhost:5500 npx playwright test \
  e2e/admin-audit-filter-export.mock.spec.ts \
  --project=chromium
```
- Result: PASS (`1 passed`)

## Verified outcomes
- `status` 过滤在后端生效，非法值返回 `400`，避免静默错误筛选。
- 前端可通过 `Task status` 快速定位目标任务并继续执行下载/取消动作。
- E2E 断言覆盖 `status` 参数传递，防止后续回归。
