# Phase 230 - Preview Rendition Async Task Governance API/UI - Verification

## Date
2026-03-08

## Scope
- 验证 Preview rendition async export 的 API/前端治理闭环：
  - `status` 过滤
  - `summary`
  - `cleanup`

## Commands and results

1. Backend controller/security tests
```bash
cd ecm-core
mvn -q -Dtest=PreviewDiagnosticsControllerSecurityTest test
```
- Result: PASS

2. Frontend lint (targeted files)
```bash
cd ecm-frontend
npm run -s lint -- src/pages/PreviewDiagnosticsPage.tsx src/services/previewDiagnosticsService.ts
```
- Result: PASS

3. Frontend build
```bash
cd ecm-frontend
npm run -s build
```
- Result: PASS

4. Mocked E2E (Chromium)
```bash
cd ecm-frontend
ECM_UI_URL=http://localhost:5500 npx playwright test e2e/admin-preview-diagnostics.mock.spec.ts --project=chromium
```
- Result: PASS

## Verified outcomes
- list/summary/cleanup 三个接口均支持 `status` 过滤并在 UI 端一致联动。
- cleanup 默认仅清理终态，非终态筛选在 API 层返回 `400`。
- mocked E2E 验证了过滤参数透传与 UI 统计变化。

