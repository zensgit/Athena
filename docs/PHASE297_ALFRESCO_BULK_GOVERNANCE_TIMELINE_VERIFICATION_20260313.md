# Phase297 Alfresco Bulk Governance Timeline（验证记录）

## 1. 验证范围
- 后端：
  - Bulk 审计事件写入逻辑（成功/部分失败）
  - Bulk history 列表与导出接口
- 前端：
  - bulkOperationService 新增接口
  - FileBrowser 时间线面板与导出动作

## 2. 执行命令与结果

### 2.1 Backend 定向测试
- 命令：
  - `cd ecm-core && mvn -Dtest=BulkOperationServiceAuditTest,BulkOperationControllerHistoryTest,SavedSearchServiceTemplateTest,SavedSearchControllerTemplateTest test`
- 结果：
  - `BUILD SUCCESS`
  - `Tests run: 9, Failures: 0, Errors: 0, Skipped: 0`

### 2.2 Frontend Lint
- 命令：
  - `cd ecm-frontend && npm run lint -- src/services/bulkOperationService.ts src/pages/FileBrowser.tsx`
- 结果：
  - 通过（exit code 0）

### 2.3 Frontend Build
- 命令：
  - `cd ecm-frontend && npm run build`
- 结果：
  - `Compiled successfully.`

## 3. 人工契约核对
- 后端 `GET /api/v1/bulk/history` 返回 `BulkHistoryResponse`（包含 `items/total/page/size`）。
- 前端 `bulkOperationService.listHistory` 已兼容 `items` 与 `content` 两种分页数据结构。
- 后端导出端点 `GET /api/v1/bulk/history/export` 返回 `text/csv` 且带附件下载头。
- 前端导出按钮调用 `api.downloadFile('/bulk/history/export', ...)`，与后端契约匹配。

## 4. 结论
- Phase297 代码与验证完成，批量治理时间线功能可用：
  - 批量执行可追溯
  - 历史可查询
  - 历史可导出
  - FileBrowser 直接可观测
