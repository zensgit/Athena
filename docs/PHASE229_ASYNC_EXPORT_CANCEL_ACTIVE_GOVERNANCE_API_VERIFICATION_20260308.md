# Phase 229 - Async Export Cancel-Active Governance API - Verification

## Date
2026-03-08

## Scope
- 验证 Audit/Ops Recovery 的 `cancel-active` API 行为与状态过滤校验。

## Commands and results

1. Analytics + Ops + Preview + Search controller tests (batch run)
```bash
cd ecm-core
mvn -q -Dtest=AnalyticsControllerTest,OpsRecoveryControllerSecurityTest,PreviewDiagnosticsControllerSecurityTest,SearchControllerSecurityTest test
```
- Result: PASS

2. Backend compile
```bash
cd ecm-core
mvn -q -DskipTests compile
```
- Result: PASS

## Verified outcomes
- Audit `cancel-active` 可批量取消活动任务，`status=QUEUED/RUNNING` 有效，其他状态 `400`。
- Ops `cancel-active` 支持 `exportType + status` 过滤，非活动状态过滤 `400`。
- 角色边界有效：Ops USER 访问 `cancel-active` 仍为 `403`。

