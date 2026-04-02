# Phase269 验证记录：Queue Declined Async Retry Dry-run 原因聚合与 CSV 导出（2026-03-11）

## 1. 验证范围
- 后端 dry-run 增强字段（`reasonBreakdown`、`reasonCode`）
- 后端 dry-run CSV 导出端点可用性与审计事件
- 前端 dry-run 导出按钮与原因展示
- mocked e2e 全链路回归

## 2. 执行命令与结果

### 2.1 Backend
```bash
cd /Users/huazhou/Downloads/Github/Athena/ecm-core
mvn -q -Dtest=PreviewDiagnosticsControllerSecurityTest,PreviewQueueServiceTest test
```
- 结果：通过

### 2.2 Frontend Lint
```bash
cd /Users/huazhou/Downloads/Github/Athena/ecm-frontend
npm run lint -- src/pages/PreviewDiagnosticsPage.tsx src/services/previewDiagnosticsService.ts e2e/admin-preview-diagnostics.mock.spec.ts
```
- 结果：通过

### 2.3 Frontend Build
```bash
cd /Users/huazhou/Downloads/Github/Athena/ecm-frontend
npm run build
```
- 结果：通过

### 2.4 Mocked E2E
```bash
cd /Users/huazhou/Downloads/Github/Athena/ecm-frontend
npx serve -s build -l 5510
ECM_UI_URL=http://localhost:5510 npx playwright test e2e/admin-preview-diagnostics.mock.spec.ts --project=chromium
```
- 结果：通过（`1 passed`）

## 3. 关键断言点
- 后端安全测试：
  - `USER` 访问 `/retry-terminal/dry-run/export` 返回 `403`
  - `ADMIN` dry-run 返回 `reasonBreakdown[]`
  - dry-run/export 返回 CSV，包含：
    - `statusFilter,limit,requested,retryable,skipped,...,reasonCode,message`
    - `reasonCode,outcome,count`
  - 审计事件包含：
    - `PREVIEW_QUEUE_DECLINED_ASYNC_EXPORT_RETRY_BULK_DRY_RUN`
    - `PREVIEW_QUEUE_DECLINED_ASYNC_EXPORT_RETRY_BULK_DRY_RUN_EXPORTED`
- mocked e2e：
  - 可以触发 `Export Dry-run CSV` 按钮
  - 显示成功提示：`Queue declined async terminal dry-run CSV exported`
  - 导出请求参数断言：`status=CANCELLED`、`limit=20`

## 4. 回归结论
- 新增能力与既有 Phase263-268 治理链路兼容，无破坏性回归。
- `retry-terminal` 的 dry-run 从“仅列表”升级为“可聚合 + 可导出”，满足运维离线分析与审计复核需求。
