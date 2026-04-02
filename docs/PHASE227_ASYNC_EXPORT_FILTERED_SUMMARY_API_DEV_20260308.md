# Phase 227 - Async Export Filtered Summary API - Development

## Date
2026-03-08

## Goal
- 让异步导出任务 `summary` 与任务列表过滤口径一致（按状态/类型过滤统计）。
- 覆盖 Audit 与 Ops Recovery 两条导出任务链路。

## Implemented

### 1) Ops Recovery summary filter support
- Updated `ecm-core/src/main/java/com/ecm/core/controller/OpsRecoveryController.java`
  - Enhanced endpoint:
    - `GET /api/v1/ops/recovery/history/export-async/summary`
  - New optional params:
    - `exportType`
    - `status`
  - Behavior:
    - no params: keep previous global summary behavior
    - with params: apply same filter semantics as task list endpoint
  - Validation:
    - `status` supports `QUEUED/RUNNING/COMPLETED/CANCELLED/FAILED` (case-insensitive)
    - invalid filter returns `400`

### 2) Audit summary filter support
- Updated `ecm-core/src/main/java/com/ecm/core/controller/AnalyticsController.java`
  - Enhanced endpoint:
    - `GET /api/v1/analytics/audit/export-async/summary`
  - New optional param:
    - `status`
  - Behavior:
    - no status: full summary
    - with status: summary scoped to matched status
  - Validation:
    - same status enum domain as task list
    - invalid status returns `400`

### 3) Backend test coverage
- Updated `ecm-core/src/test/java/com/ecm/core/controller/OpsRecoveryControllerSecurityTest.java`
  - Added admin assertions for summary filters:
    - `status=completed` -> `200`
    - `status=invalid` -> `400`
    - `exportType=HISTORY_SUMMARY` -> `200`
- Updated `ecm-core/src/test/java/com/ecm/core/controller/AnalyticsControllerTest.java`
  - Added summary filter tests:
    - default summary unchanged
    - `status=completed` -> `200`
    - `status=invalid` -> `400`

## Impact
- summary 与列表过滤口径统一，运维判断不再出现“列表已过滤但统计是全量”的偏差。
- 为后续任务中心自动化治理（按过滤策略清理/告警）提供稳定契约。
