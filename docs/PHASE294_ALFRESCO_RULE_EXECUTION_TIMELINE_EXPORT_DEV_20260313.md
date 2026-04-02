# Phase 294 - Alfresco 对标：Rule Execution Timeline + CSV Export（Dev）

## Date
- 2026-03-13

## Goal
- 在 Phase293（manual execution + idempotency ledger）基础上，补齐“规则执行时间线查询与导出”能力。
- 支撑运维审计复盘：按规则/文档/触发器/执行人/时间窗口筛选，并导出 CSV。

## Scope

### Backend API
- 增强列表接口（兼容旧路径）：
  - `GET /api/v1/rules/executions`
  - `GET /api/v1/rules/executions/timeline`
- 新增导出接口（兼容双路径）：
  - `GET /api/v1/rules/executions/export`
  - `GET /api/v1/rules/executions/timeline/export`
- 支持过滤参数：
  - `ruleId`
  - `documentId`
  - `triggerType`
  - `success`
  - `actor`
  - `from`
  - `to`
  - `limit`

### Backend Service
- `RuleEngineService` 新增时间线查询模型：
  - `RuleRunTimelineQuery`
- `listRuleRuns(...)` 升级为可按时间线维度过滤（保留 `listRuleRuns(ruleId, limit)` 兼容入口）。
- `RuleRunLedgerRecord` 增加 `executedBy` 字段。
- `executeRuleManual(...)` 将当前用户写入台账，保持审计事件一致。

### Backend Audit
- 新增导出审计事件：
  - `RULE_MANUAL_RUN_TIMELINE_EXPORTED`

### Frontend
- `RulesPage` 的 `Manual Execution Ledger` 区域新增时间线过滤：
  - `Rule ID`
  - `Actor`
  - `Success`（ALL/SUCCESS/FAILED）
  - `From/To`（datetime-local）
  - `Limit`
- 新增操作：
  - `Refresh Timeline`
  - `Export CSV`
- `ruleService` 新增：
  - `listRuleExecutionTimeline(filters)`
  - `exportRuleExecutionTimelineCsv(filters)`

## Compatibility
- 保留原有 `GET /rules/executions` 行为，新增 timeline 别名路径，不破坏旧前端调用。
- 旧 manual execute 流程保持不变。

## Alfresco Parity Mapping
- 对齐 Alfresco 规则治理中“可筛选运行记录 + 导出复盘”能力方向；
- 在 Athena 中以轻量 ledger + 审计事件方式实现更快迭代闭环。
