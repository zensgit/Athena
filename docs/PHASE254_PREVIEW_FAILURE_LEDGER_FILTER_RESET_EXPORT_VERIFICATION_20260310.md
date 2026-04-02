# Phase 254 - Preview Failure Ledger Filter Reset & CSV Export (Verification)

Date: 2026-03-10

## 1. Backend verification

Command:

```bash
cd ecm-core
mvn -q -Dtest=PreviewDiagnosticsControllerSecurityTest test
```

Result:

- PASSED
- 覆盖确认：
  - 新增接口权限门禁生效（ADMIN 可用，USER forbidden）。
  - `reset-by-filter` 返回统计字段与逐条结果。
  - `ledger/export` 返回 CSV 与 `X-Preview-Failure-Ledger-Count`。
  - 新增审计事件已触发（`PREVIEW_FAILURE_LEDGER_RESET_BY_FILTER` / `PREVIEW_FAILURE_LEDGER_EXPORTED`）。

## 2. Frontend verification

Command:

```bash
cd ecm-frontend
npm run -s lint -- src/pages/PreviewDiagnosticsPage.tsx src/services/previewDiagnosticsService.ts e2e/admin-preview-diagnostics.mock.spec.ts
```

Result:

- PASSED

Command:

```bash
cd ecm-frontend
npm run -s build
```

Result:

- PASSED

Command:

```bash
cd ecm-frontend
npx serve -s build -l 5510
ECM_UI_URL=http://localhost:5510 npx playwright test e2e/admin-preview-diagnostics.mock.spec.ts --project=chromium
```

Result:

- PASSED（1 test）
- 覆盖确认：
  - reason 级 `Reset Ledger` 按钮触发 `reset-by-filter` 接口。
  - failure-ledger 面板 `Export Ledger CSV` 触发导出接口。
  - mock 断言捕获到 `days/limit` 与 reason filter 参数传递。

## 3. Delivery notes

Phase254 完成后，failure-ledger 控制面具备：

- persisted ledger 可视化（Phase253）
- 单条/批量 reset（Phase253）
- reason 维度 reset（Phase254）
- CSV 导出 + 审计追踪（Phase254）

已形成从定位到治理到留痕的闭环。

