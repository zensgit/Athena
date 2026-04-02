# Phase 255 - Preview Queue Health Diagnostics (Verification)

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
  - `GET /preview/diagnostics/queue/summary` 权限门禁正确。
  - queue summary DTO 字段与样本项结构可用。
  - memory backend 下 snapshot 统计逻辑正确（scheduled/governance/running/cancelRequested）。

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
  - `Preview Queue Health` 卡片渲染；
  - queue summary 指标（Scheduled/Cancel requested）可见；
  - mocked 请求参数 `limit=20` 被捕获。

## 3. Delivery notes

Phase255 完成后，preview diagnostics 在队列治理面具备：

- 队列实时健康摘要；
- 去重治理 key 可视化；
- 运行/取消请求状态可视化；
- 与 failure-ledger / dead-letter 形成统一治理视图。

