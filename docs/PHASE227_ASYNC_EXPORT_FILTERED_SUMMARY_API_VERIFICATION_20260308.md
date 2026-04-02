# Phase 227 - Async Export Filtered Summary API - Verification

## Date
2026-03-08

## Scope
- 验证 Ops Recovery 与 Audit 的 async export summary 过滤能力。
- 验证非法过滤参数返回 `400`。

## Commands and results

1. Ops recovery controller security tests
```bash
cd ecm-core
mvn -q -Dtest=OpsRecoveryControllerSecurityTest test
```
- Result: PASS

2. Analytics controller tests
```bash
cd ecm-core
mvn -q -Dtest=AnalyticsControllerTest test
```
- Result: PASS

3. Backend compile
```bash
cd ecm-core
mvn -q -DskipTests compile
```
- Result: PASS

## Verified outcomes
- Ops summary 支持 `exportType + status` 组合过滤，非法 `status` 返回 `400`。
- Audit summary 支持 `status` 过滤，非法 `status` 返回 `400`。
- 默认无过滤参数时，summary 行为保持兼容。
