# Phase 295 - Alfresco 对标：Rule Audit Timeline + CSV Export（Dev）

## Date
- 2026-03-13

## Goal
- 在 Phase294（rule run timeline）基础上补齐规则治理审计视角：
  - 面向 `audit_log` 的规则审计时间线查询；
  - 支持筛选与 CSV 导出，用于运维复盘与合规留痕。

## Scope

### Backend Repository
- `AuditLogRepository` 新增：
  - `findRuleAuditTimeline(eventType, username, nodeId, from, to, pageable)`
- 过滤范围限定为：
  - `RULE_%`
  - `SCHEDULED_RULE%`

### Backend API
- `RuleController` 新增：
  - `GET /api/v1/rules/executions/audit`
  - `GET /api/v1/rules/executions/audit/export`（`text/csv`）
- 支持过滤参数：
  - `eventType`
  - `actor`
  - `nodeId`
  - `from`
  - `to`
  - `limit`

### Backend Data Model / Response
- 新增响应 DTO：
  - `RuleAuditTimelineItemResponse`
  - 字段：`id/eventType/nodeId/nodeName/username/eventTime/details`

### Backend Audit Event
- 导出行为新增审计：
  - `RULE_AUDIT_TIMELINE_EXPORTED`

### Frontend (parallel track)
- `ruleService` 新增：
  - `listRuleAuditTimeline(filters)`
  - `exportRuleAuditTimelineCsv(filters)`
- `RulesPage` 新增 `Rule Audit Timeline` 面板：
  - 过滤 + 刷新 + 导出 + 列表展示。

## Compatibility
- 本增量不改变既有 Rule CRUD / dry-run / manual execution 链路；
- 与 Phase294 的 run ledger timeline 能力互补（运行台账 vs 审计事件）。
