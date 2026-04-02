# Phase 293 - Alfresco 对标：Rule Manual Execution + Idempotency Ledger（Dev）

## Date
- 2026-03-13

## Goal
- 补齐规则引擎的“手动执行与运行台账”能力，支撑运维调试与可追踪执行。
- 为规则执行增加幂等键去重能力，避免重复人工触发导致重复执行。

## Scope

### Backend API
- 新增手动执行入口：
  - `POST /api/v1/rules/{ruleId}/execute`
  - 入参：`documentId`, `triggerType`, `idempotencyKey`
  - 出参：`runId`, `deduplicated`, `deduplicatedFromRunId`, `run`
- 新增台账查询接口：
  - `GET /api/v1/rules/executions?ruleId=&limit=`
  - `GET /api/v1/rules/executions/{runId}`

### Backend Service
- `RuleEngineService` 新增：
  - `executeRuleManual(...)`
  - `listRuleRuns(...)`
  - `getRuleRun(...)`
- 新增内存台账结构：
  - `ruleRunLedgerById`
  - `ruleRunLedgerOrder`
  - `ruleRunIdempotencyIndex`
- 幂等语义：
  - 同 `(ruleId, documentId, triggerType, idempotencyKey)` 命中时复用已有 run，返回 `deduplicated=true`
- 审计事件：
  - `RULE_MANUAL_RUN_EXECUTED`
  - `RULE_MANUAL_RUN_REUSED`

### Frontend
- `RulesPage` 新增 `Manual Execution Ledger (Idempotency)` 面板：
  - 输入 Rule ID / Document ID / Trigger / Idempotency Key
  - 执行按钮 + Ledger 刷新按钮
  - 展示 recent runs（run/rule/document/trigger/matched/success/actions/duration）
- `ruleService` 新增 API：
  - `executeRuleManually`
  - `listRuleExecutions`
  - `getRuleExecution`

## Security
- 手动执行与台账查询接口均要求 `ADMIN/EDITOR`。

## Compatibility
- 增量能力，不改变现有自动触发规则链路。
- 台账为服务内存级短期 ledger（不改变持久化 schema）。
