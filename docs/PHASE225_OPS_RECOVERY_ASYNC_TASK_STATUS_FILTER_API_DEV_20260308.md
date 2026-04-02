# Phase 225 - Ops Recovery Async Task Status Filter API - Development

## Date
2026-03-08

## Goal
- 为 Ops Recovery 异步导出任务列表增加 `status` 过滤能力，支持按状态快速定位任务。
- 与已有 `exportType` 过滤组合，形成可组合查询能力。

## Implemented

### 1) List endpoint supports status filter
- Updated `ecm-core/src/main/java/com/ecm/core/controller/OpsRecoveryController.java`
  - Enhanced endpoint:
    - `GET /api/v1/ops/recovery/history/export-async`
  - New optional query parameter:
    - `status`
  - Accepted values (case-insensitive):
    - `QUEUED`
    - `RUNNING`
    - `COMPLETED`
    - `CANCELLED`
    - `FAILED`
  - Invalid values:
    - return `400`

### 2) Combined filter logic
- Updated `ecm-core/src/main/java/com/ecm/core/controller/OpsRecoveryController.java`
  - Internal listing now applies:
    - `exportType` filter (if provided)
    - `status` filter (if provided)
  - Both filters can be active simultaneously.

### 3) Normalization helper reuse
- Updated `ecm-core/src/main/java/com/ecm/core/controller/OpsRecoveryController.java`
  - Added reusable `status` normalization for list filter.
  - Cleanup status normalization now reuses shared helper and keeps terminal-only cleanup guard.

### 4) Security/controller tests
- Updated `ecm-core/src/test/java/com/ecm/core/controller/OpsRecoveryControllerSecurityTest.java`
  - Added ADMIN coverage:
    - `status=completed` returns `200`
    - `status=invalid` returns `400`
  - Existing USER forbid coverage remains intact.

## Impact
- 异步任务中心后端支持按状态检索，提升排障与治理效率。
- 与 `exportType` 组合过滤后，任务列表可读性明显提升。
