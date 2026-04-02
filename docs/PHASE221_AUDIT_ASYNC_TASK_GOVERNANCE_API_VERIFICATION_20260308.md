# Phase 221 - Audit Async Task Governance API - Verification

## Date
2026-03-08

## Scope
- 验证 audit async task summary/cleanup 接口行为与输入约束。
- 验证 controller 级别自动化覆盖。

## Commands and results

1. Backend controller tests
```bash
cd ecm-core
mvn -q -Dtest=AnalyticsControllerTest test
```
- Result: PASS

2. Backend compile
```bash
cd ecm-core
mvn -q -DskipTests compile
```
- Result: PASS

## Verified outcomes
- summary 接口可返回完整状态分布和 active/terminal 汇总计数。
- cleanup 默认仅清理 terminal 任务，不影响运行态任务。
- cleanup 对非 terminal 状态筛选（`RUNNING/QUEUED`）返回 `400`。
