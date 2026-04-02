# Phase 235 - Preview Async Task Center Mock E2E Auto-Start Gate - Development

## Date
2026-03-09

## Goal
- 把 Preview/Advanced Search 相关 mocked e2e 从“依赖外部已启动 UI”升级为“一键自启动 + 自清理”执行模式。
- 降低 `ECM_UI_URL` 未启动导致的误失败（`ERR_CONNECTION_REFUSED`），提升并行开发验证效率。

## Implemented

### 1) Added one-command mocked e2e gate script
- Added `scripts/phase235-preview-async-task-center-mock-e2e.sh`
  - Starts frontend dev server automatically:
    - `CI=true BROWSER=none npm start`
  - Waits for UI readiness by polling `ECM_UI_HEALTH_URL` (default equals `ECM_UI_URL`).
  - Runs Playwright suite with explicit base URL:
    - `ECM_UI_URL=<url> npx playwright test ...`
  - Ensures server cleanup via `trap` on exit/failure.

### 2) Script defaults and override controls
- Default test set (when no args supplied):
  - `e2e/admin-preview-diagnostics.mock.spec.ts`
  - `e2e/advanced-search-preview-batch-scope.mock.spec.ts`
- Tunable env vars:
  - `ECM_UI_URL` (default `http://localhost:3000`)
  - `ECM_UI_HEALTH_URL`
  - `ECM_UI_WAIT_SECONDS` (default `180`)
  - `ECM_E2E_PROJECT` (default `chromium`)
- Also supports passing custom test files as positional args.

### 3) Delivery impact
- Preview async task-center governance回归（start/list/summary/cancel/download/cleanup/cancel-active） now has a stable local runner.
- Eliminates repeated manual steps for frontend bootstrapping during parallel backend/frontend iteration.

## Files
- `scripts/phase235-preview-async-task-center-mock-e2e.sh`
