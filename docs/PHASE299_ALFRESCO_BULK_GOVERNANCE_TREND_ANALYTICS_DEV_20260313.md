# Phase299 Alfresco Bulk Governance Trend Analytics（开发设计）

## 1. 目标
- 在 Phase298（Bulk Summary）基础上补齐趋势治理能力：
  - bulk 历史趋势 API（按天聚合）
  - bulk 历史趋势 CSV 导出
  - FileBrowser 面板内联趋势可视化与导出

## 2. 后端实现

### 2.1 Bulk Trend API + CSV
- 文件：`ecm-core/src/main/java/com/ecm/core/controller/BulkOperationController.java`
- 新增接口：
  - `GET /api/v1/bulk/history/summary/trend`
  - `GET /api/v1/bulk/history/summary/trend/export`
- 过滤维度：
  - `eventType/actor/nodeId/from/to`
- 返回结构：
  - `items[]`（`date/count`）
  - `truncated`（超过扫描上限时为 true）
  - `scanLimit`
- 导出结构：
  - CSV header: `date,count`
- 审计事件：
  - `BULK_HISTORY_TREND_EXPORTED`

### 2.2 聚合策略
- 不新增数据库方言依赖，复用现有 timeline 查询并在控制器端按天聚合：
  - 扫描分页：`pageSize=500`
  - 扫描上限：`MAX_HISTORY_TREND_SCAN=20000`
  - 分页拉取并将 `eventTime.toLocalDate()` 计数到趋势桶

### 2.3 后端测试
- 文件：`ecm-core/src/test/java/com/ecm/core/controller/BulkOperationControllerHistoryTest.java`
- 新增覆盖：
  - trend 查询接口的按天聚合结果
  - trend 导出 CSV 响应与审计事件写入

## 3. 前端实现

### 3.1 服务层扩展
- 文件：`ecm-frontend/src/services/bulkOperationService.ts`
- 新增：
  - `listHistoryTrend(params?)`
  - `exportHistoryTrendCsv(params?)`
- 新增类型：
  - `BulkHistoryTrendItem`
  - `BulkHistoryTrendResult`
- 字段兼容归一化：
  - 日期字段：`date/day/bucketDate`
  - 计数字段：`count/total`

### 3.2 FileBrowser Bulk 面板增强
- 文件：`ecm-frontend/src/pages/FileBrowser.tsx`
- 新增：
  - `Trend` 区块（按天 chips）
  - `Export Trend CSV` 按钮
  - `Trend truncated` 告警 chip（当后端返回截断）
- 刷新策略：
  - history + summary + trend 并行加载（`Promise.allSettled`）

## 4. 对标价值
- 从“时间线 + 汇总”升级到“时间线 + 汇总 + 趋势”，进一步对齐并超越 Alfresco 风格治理可观测性。
- 管理员可快速判断：
  - bulk 活动是否在上升/下降
  - 哪些日期出现操作高峰
  - 是否存在需要扩容或审计复盘的周期性波动
