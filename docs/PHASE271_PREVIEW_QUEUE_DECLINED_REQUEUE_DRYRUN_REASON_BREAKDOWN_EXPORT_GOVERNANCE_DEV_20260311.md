# Phase271 开发设计：Preview Queue Declined Requeue Dry-run Reason Breakdown + CSV Governance（2026-03-11）

## 1. 背景与目标

在 Phase260 完成 `queue/declined/requeue/dry-run` 基础能力后，本阶段补齐“可解释 + 可导出 + 可审计”闭环，避免 dry-run 只给总计、不利于大规模治理复盘：

1. 逐条结果增加 `reasonCode`，明确为什么 `QUEUED/SKIPPED/FAILED`。
2. 返回 `reasonBreakdown[]`，支持按原因聚合观察。
3. 新增 dry-run CSV 导出能力（含明细 + 聚合）。
4. 增加导出审计事件，保证治理操作可追踪。
5. 前端增加一键导出与原因可视化，mocked e2e 覆盖。

## 2. 设计范围

### 2.1 后端 API（`PreviewDiagnosticsController`）

补齐并增强以下链路：

1. `POST /api/v1/preview/diagnostics/queue/declined/requeue/dry-run`
2. `GET /api/v1/preview/diagnostics/queue/declined/requeue/dry-run/export`

新增/调整 DTO：

1. `PreviewQueueDeclinedRequeueDryRunItemDto`
   - 新增 `reasonCode`
2. `PreviewQueueDeclinedRequeueDryRunResponseDto`
   - 新增 `reasonBreakdown`
3. 新增 `PreviewQueueDeclinedRequeueDryRunReasonCountDto`
   - `reasonCode/outcome/count`

新增核心方法：

1. `computeQueueDeclinedRequeueDryRun(...)`
   - 统一计算 dry-run 明细、汇总计数与 `reasonBreakdown`
2. `deriveQueueDeclinedRequeueDryRunReasonCode(...)`
   - 根据 queue 评估 message/status 归一化 reason code
3. `buildQueueDeclinedRequeueDryRunCsv(...)`
   - 输出“明细区 + reason breakdown 区”双段 CSV
4. `auditQueueDeclinedRequeueDryRunExport(...)`
   - 新审计事件：`PREVIEW_QUEUE_DECLINED_REQUEUE_DRY_RUN_EXPORTED`

审计增强：

1. `PREVIEW_QUEUE_DECLINED_REQUEUE_DRY_RUN` 增加 `reasonBreakdown` 摘要。
2. 导出事件记录过滤上下文与估算计数，确保复盘可追踪。

### 2.2 前端（Preview Diagnostics）

1. service 层 dry-run 类型补齐：
   - item `reasonCode`
   - response `reasonBreakdown[]`
2. 新增 service 导出方法：
   - `exportQueueDeclinedRequeueDryRunCsv(...)`
3. 页面新增操作按钮：
   - `Export Requeue Dry-run CSV`（带 loading 防重入）
4. 页面新增可视化：
   - dry-run Alert 下展示前 5 个 reason breakdown chips
   - dry-run 结果表新增 `Reason Code` 列

### 2.3 mocked e2e

1. `/queue/declined/requeue/dry-run` mock 返回 `reasonCode + reasonBreakdown`
2. 新增 `/queue/declined/requeue/dry-run/export` mock CSV
3. 主流程增加导出按钮点击与成功提示断言

## 3. 兼容性与风险控制

1. 新字段均为扩展字段，不破坏既有调用参数。
2. 导出接口为新增能力，不影响已有 dry-run 行为。
3. reasonCode 使用稳定字符串常量，便于后续监控聚合与前端映射。
4. 保持 `limit/category/forceRequired/query/windowHours/force` 过滤上下文与现有治理链路一致。

## 4. 对标/超越 Alfresco 的价值

相较“仅列表+手动判断”的传统诊断方式，本阶段在 dry-run 链路提供：

1. 结构化 reason code（机器可统计）而非纯文本 message。
2. 聚合 breakdown 与明细同时输出（UI+CSV 双通道）。
3. 导出审计事件闭环，满足治理流程可复核要求。
