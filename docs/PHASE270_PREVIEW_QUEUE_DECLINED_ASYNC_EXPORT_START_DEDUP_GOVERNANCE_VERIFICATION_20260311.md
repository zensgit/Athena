# Phase270 验证记录：Queue Declined Async Export Start 去重复用治理（2026-03-11）

## 1. 验证范围
- start 去重复用命中行为（后端 + 前端 + mocked e2e）
- create 响应扩展字段兼容性（`deduplicated` 等）
- dedup 审计事件写入
- 既有 queue declined async export 流程无回归

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

## 3. 关键断言
- 后端 controller security/admin 路径：
  - 第二次 start（同过滤、前一个任务活跃）返回：
    - `deduplicated=true`
    - `deduplicatedFromTaskId=<existingTaskId>`
  - 审计事件包含：
    - `PREVIEW_QUEUE_DECLINED_ASYNC_EXPORT_START_DEDUP_HIT`
    - 含 `reusedTaskId=...` 与过滤上下文
- mocked e2e：
  - 连续两次 Start，第二次出现提示：
    - `Queue declined async export task reused: ...`
  - 断言去重调用记录数组非空，且复用任务 id 正确

## 4. 结论
- start 去重复用治理已生效，且与 Phase263-269 现有治理能力兼容。
- 在高频点击或重复批量操作场景下，可显著减少重复异步任务创建与后续治理成本。
