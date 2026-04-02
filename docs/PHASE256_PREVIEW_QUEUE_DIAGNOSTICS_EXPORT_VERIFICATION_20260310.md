# Phase 256 - Preview Queue Diagnostics CSV Export (Verification)

Date: 2026-03-10

## 1. Backend verification

Command:

```bash
cd ecm-core
mvn -q -Dtest=PreviewDiagnosticsControllerSecurityTest,PreviewQueueServiceTest test
```

Result:

- PASSED
- 覆盖确认：
  - `GET /preview/diagnostics/queue/summary/export` 权限门禁正确。
  - 导出接口返回 CSV 与 `X-Preview-Queue-Item-Count`。
  - 审计事件 `PREVIEW_QUEUE_DIAGNOSTICS_EXPORTED` 已触发。

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
  - Queue Health 卡片导出按钮可用；
  - 导出成功 toast 可见；
  - 请求参数 `limit=200` 已被断言。

## 3. Delivery notes

Phase256 将 Queue Health 从“可观测”升级为“可导出+可审计”，满足并行运维场景下的留档与复盘需求。

