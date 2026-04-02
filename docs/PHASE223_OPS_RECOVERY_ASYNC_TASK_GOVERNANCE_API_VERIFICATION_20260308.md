# Phase 223 - Ops Recovery Async Task Governance API - Verification

## Date
2026-03-08

## Scope
- 验证 ops recovery async export summary/cleanup 接口行为与输入约束。
- 验证 ADMIN/USER 权限控制。

## Commands and results

1. Ops recovery controller security tests
```bash
cd ecm-core
mvn -q -Dtest=OpsRecoveryControllerSecurityTest test
```
- Result: PASS

2. Backend compile
```bash
cd ecm-core
mvn -q -DskipTests compile
```
- Result: PASS

## Verified outcomes
- summary 接口返回完整状态分布统计及 active/terminal 汇总。
- cleanup 默认仅清理终态任务；支持 `exportType`、`status` 过滤。
- cleanup 拒绝运行态筛选（`QUEUED/RUNNING`），返回 `400`。
- USER 对 summary/cleanup 接口访问被拒绝（`403`）。
