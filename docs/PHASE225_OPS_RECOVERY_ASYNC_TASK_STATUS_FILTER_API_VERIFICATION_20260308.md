# Phase 225 - Ops Recovery Async Task Status Filter API - Verification

## Date
2026-03-08

## Scope
- 验证 ops recovery async task list 的 status 过滤能力。
- 验证非法 status 输入返回 `400`。

## Commands and results

1. Backend controller security test
```bash
cd ecm-core
mvn -q -Dtest=OpsRecoveryControllerSecurityTest test
```
- Result: PASS

## Verified outcomes
- list 接口支持 `status` 过滤（大小写不敏感）。
- `status=invalid` 返回 `400`。
- `exportType + status` 组合过滤逻辑可用。
