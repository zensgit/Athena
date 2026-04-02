# Phase298 Alfresco Bulk Governance Summary Analytics（开发设计）

## 1. 目标
- 在 Phase297 的 bulk 时间线基础上，补齐治理统计能力：
  - bulk 历史 summary API（总量 + top eventType + top actor）
  - bulk 历史 summary CSV 导出
  - FileBrowser 面板内联 summary 可视化

## 2. 后端实现

### 2.1 Repository 聚合查询
- 文件：`ecm-core/src/main/java/com/ecm/core/repository/AuditLogRepository.java`
- 新增：
  - `countBulkByEventTypeWithFilters(...)`
  - `countBulkByUsernameWithFilters(...)`
- 过滤维度：
  - `eventType/username(node actor)/nodeId/from/to`
  - 固定约束：`UPPER(eventType) LIKE 'BULK_%'`

### 2.2 Bulk Summary API + CSV
- 文件：`ecm-core/src/main/java/com/ecm/core/controller/BulkOperationController.java`
- 新增接口：
  - `GET /api/v1/bulk/history/summary`
  - `GET /api/v1/bulk/history/summary/export`
- 返回结构：
  - `total`
  - `eventTypeItems[]`（`key/count`）
  - `actorItems[]`（`key/count`）
- 导出结构：
  - CSV header: `section,key,count`
  - `meta,total,*` + `eventType,*` + `actor,*`
- 审计事件：
  - `BULK_HISTORY_SUMMARY_EXPORTED`

### 2.3 后端测试
- 文件：`ecm-core/src/test/java/com/ecm/core/controller/BulkOperationControllerHistoryTest.java`
- 新增覆盖：
  - summary 查询接口返回字段正确
  - summary 导出接口 CSV 与审计事件写入

## 3. 前端实现

### 3.1 服务层扩展
- 文件：`ecm-frontend/src/services/bulkOperationService.ts`
- 新增：
  - `listHistorySummary(params?)`
  - `exportHistorySummaryCsv(params?)`
  - summary 类型定义和字段兼容归一化（`eventType/type`、`actor/username`、`total/totalEvents`）

### 3.2 FileBrowser Summary 区块
- 文件：`ecm-frontend/src/pages/FileBrowser.tsx`
- 新增：
  - `Summary` 区块（Total events / eventType top chips / actor top chips）
  - `Export Summary CSV` 按钮
  - 刷新策略：history + summary 并行加载（`Promise.allSettled`）

## 4. 对标价值
- 从“仅有事件流水”提升到“流水 + 聚合分析 + 导出”，更接近 Alfresco 管理台的治理可观测体验。
- 支持管理员快速回答：
  - 最近 bulk 事件总量是多少
  - 主要操作类型是什么
  - 主要操作人是谁
