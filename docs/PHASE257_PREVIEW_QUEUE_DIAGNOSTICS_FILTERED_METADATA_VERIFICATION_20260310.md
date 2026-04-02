# Phase 257 - Preview Queue Diagnostics Filtered Metadata (Verification)

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
  - `/preview/diagnostics/queue/summary` 支持 `state/query` 过滤；
  - 返回项包含 name/path/mimeType/previewStatus/queueState；
  - `/queue/summary/export` 输出与过滤条件对齐；
  - 审计事件 `PREVIEW_QUEUE_DIAGNOSTICS_EXPORTED` 触发且包含过滤上下文。

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
  - Queue Health 支持状态 + query 过滤；
  - 过滤后的样本计数与列表显示正确；
  - 导出请求携带 `state/query` 并已断言。

## 3. Delivery notes

Phase257 将 Queue Health 从“摘要样本表”升级为“可筛选、可定位、可按筛选导出”的运维面板，补齐并行治理场景下的操作闭环。
