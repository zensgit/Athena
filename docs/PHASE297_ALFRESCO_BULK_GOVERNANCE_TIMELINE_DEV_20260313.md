# Phase297 Alfresco Bulk Governance Timeline（开发设计）

## 1. 目标
- 对标 Alfresco 的批量操作可追溯能力，补齐 Athena 的 Bulk 操作治理闭环：
  - 批量操作写审计（成功/部分成功/失败）
  - 批量治理时间线查询
  - 批量治理时间线 CSV 导出
  - 文件浏览页直接可见最近批量治理事件

## 2. 后端设计与实现

### 2.1 批量操作审计事件标准化
- 文件：
  - `ecm-core/src/main/java/com/ecm/core/service/BulkOperationService.java`
  - `ecm-core/src/main/java/com/ecm/core/service/BulkMetadataService.java`
- 设计：
  - 为 bulk 结果统一生成 `BULK_*` 事件：
    - 全成功：`*_COMPLETED`
    - 部分失败：`*_PARTIAL`
    - 全失败：`*_FAILED`
  - 详情字段统一写入 `requested/success/failed`，失败时附 `failedSample`。
  - 使用 `SecurityService#getCurrentUser()` 作为审计 actor，兜底 `system`。
- 关键位置：
  - `BulkOperationService` 审计逻辑：`line 110`
  - `BulkMetadataService` 审计逻辑：`line 129`

### 2.2 Bulk 历史查询与导出 API
- 文件：
  - `ecm-core/src/main/java/com/ecm/core/controller/BulkOperationController.java`
  - `ecm-core/src/main/java/com/ecm/core/repository/AuditLogRepository.java`
- 新增 API：
  - `GET /api/v1/bulk/history`
  - `GET /api/v1/bulk/history/export`（`text/csv`）
- 过滤条件：
  - `eventType/actor/nodeId/from/to`
  - 列表支持 `page/size`，导出支持 `limit`
- 鉴权：
  - Bulk 写接口与 history/export 统一 `ADMIN/EDITOR`。
- SQL 兼容设计：
  - 新增 `findBulkOperationTimelineNoNodeId(...)`，规避 PostgreSQL 对 `NULL UUID` 类型推断问题（与已有审计查询设计保持一致）。

## 3. 前端设计与实现

### 3.1 Bulk 服务层
- 文件：`ecm-frontend/src/services/bulkOperationService.ts`
- 新增能力：
  - `bulkDelete(ids)`
  - `listHistory(params)`
  - `exportHistoryCsv(params)`
- 兼容处理：
  - 历史结果支持 `items`（当前后端契约）与 `content`（历史分页形态）双格式兼容解析。

### 3.2 FileBrowser 治理面板
- 文件：`ecm-frontend/src/pages/FileBrowser.tsx`
- 变更：
  - 批量删除改为调用 `/bulk/delete`，回显 success/failure 计数。
  - 新增 `Bulk Governance Timeline` 面板（Event Type / Actor / Time / Details）。
  - 支持手动刷新与 CSV 导出。
  - 页面初次加载、bulk delete、bulk metadata 完成后自动刷新治理时间线。

## 4. 对标增量
- 相比之前仅有 bulk 执行无治理可见性，本阶段补齐：
  - 执行 -> 审计 -> 查询 -> 导出的完整链路。
- 可直接支撑运维侧批量操作追踪、复盘和离线审计。

## 5. 变更清单
- Backend
  - `ecm-core/src/main/java/com/ecm/core/controller/BulkOperationController.java`
  - `ecm-core/src/main/java/com/ecm/core/repository/AuditLogRepository.java`
  - `ecm-core/src/main/java/com/ecm/core/service/BulkOperationService.java`
  - `ecm-core/src/main/java/com/ecm/core/service/BulkMetadataService.java`
  - `ecm-core/src/test/java/com/ecm/core/controller/BulkOperationControllerHistoryTest.java`
  - `ecm-core/src/test/java/com/ecm/core/service/BulkOperationServiceAuditTest.java`
- Frontend
  - `ecm-frontend/src/services/bulkOperationService.ts`
  - `ecm-frontend/src/pages/FileBrowser.tsx`
