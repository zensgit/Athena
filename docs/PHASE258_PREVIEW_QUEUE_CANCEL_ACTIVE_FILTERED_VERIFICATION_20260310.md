# Phase 258 - Preview Queue Cancel-Active by Filter (Verification)

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
  - `POST /preview/diagnostics/queue/cancel-active` 权限门禁正确；
  - ADMIN 可按 `state/query` 触发批量取消；
  - 返回聚合统计与逐项结果正确；
  - 审计事件 `PREVIEW_QUEUE_CANCEL_ACTIVE` 已触发。

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
  - Queue Health `Cancel Filtered` 可用；
  - 成功 toast 可见；
  - cancel-active 请求参数 `limit/state/query` 已被断言。

## 3. Delivery notes

Phase258 让 Queue Health 从“诊断+导出”进一步升级为“诊断+导出+治理执行”，支持按过滤条件直接进行批量取消操作。
