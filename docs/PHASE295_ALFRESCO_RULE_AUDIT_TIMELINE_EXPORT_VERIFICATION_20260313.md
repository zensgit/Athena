# Phase 295 - Alfresco 对标：Rule Audit Timeline + CSV Export（Verification）

## Date
- 2026-03-13

## Scope
- 验证规则审计时间线与导出能力（`/api/v1/rules/executions/audit*`）在前后端链路可用。
- 验证本期增量未破坏既有规则治理能力（folder scope / manual execution ledger / action definitions）。

## Automated Verification

### 1) Backend targeted regression
- Command:
```bash
cd ecm-core && mvn -Dtest=RuleControllerActionDefinitionsTest,RuleControllerActionDefinitionsSecurityTest,RuleControllerFolderScopeTest,RuleControllerFolderScopeSecurityTest,RuleControllerExecutionLedgerTest,RuleControllerExecutionLedgerSecurityTest,RuleControllerRuleAuditTimelineTest,RuleControllerRuleAuditTimelineSecurityTest,RuleEngineServiceExecutionLedgerTest,RuleEngineServiceFolderScopeTest test
```
- Result:
  - `BUILD SUCCESS`
  - `Tests run: 39, Failures: 0, Errors: 0, Skipped: 0`

### 2) Frontend lint (targeted)
- Command:
```bash
cd ecm-frontend && npm run lint -- src/pages/RulesPage.tsx src/services/ruleService.ts
```
- Result:
  - Exit code `0`
  - 无 lint error/warning（命令通过）

### 3) Frontend production build
- Command:
```bash
cd ecm-frontend && npm run build
```
- Result:
  - `Compiled successfully.`
  - 产物生成于 `ecm-frontend/build/`

## API Contract Checks
- `GET /api/v1/rules/executions/audit`
  - 支持 `eventType/actor/nodeId/from/to/limit` 过滤；
  - 返回按 `eventTime DESC` 的规则审计事件。
- `GET /api/v1/rules/executions/audit/export`
  - 返回 `text/csv; charset=UTF-8`；
  - `Content-Disposition: attachment; filename=rule-audit-timeline-*.csv`。

## UI Checks
- `RulesPage` 新增 `Rule Audit Timeline` 区块：
  - 过滤字段：`Event Type / Actor / Node ID / From / To / Limit`
  - 操作按钮：`Refresh Audit`、`Export Audit CSV`
  - 表格字段：`Event Time / Event Type / Username / Node / Details`
- 执行手工规则后会触发审计列表刷新（与 run ledger 刷新保持一致）。

## Conclusion
- Phase295 功能实现与验证通过，可用于规则审计追踪与合规导出场景。
