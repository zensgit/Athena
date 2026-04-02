# Phase 229 - Async Export Cancel-Active Governance API - Development

## Date
2026-03-08

## Goal
- 为异步导出任务中心补齐批量治理动作 `cancel-active`，覆盖 Audit 与 Ops Recovery。
- 让运维可以一次性取消活动任务（`QUEUED/RUNNING`），并保留可过滤能力。

## Implemented

### 1) Audit async export cancel-active API
- Updated `ecm-core/src/main/java/com/ecm/core/controller/AnalyticsController.java`
  - Added endpoint:
    - `POST /api/v1/analytics/audit/export-async/cancel-active`
  - Behavior:
    - default: cancel all active (`QUEUED/RUNNING`) tasks
    - optional `status`: only supports `QUEUED` or `RUNNING`
    - non-active status (for example `COMPLETED`) returns `400`
  - Response:
    - `cancelledCount`
    - `remainingActiveCount`
    - `statusFilter`
    - `message`

### 2) Ops recovery async export cancel-active API
- Updated `ecm-core/src/main/java/com/ecm/core/controller/OpsRecoveryController.java`
  - Added endpoint:
    - `POST /api/v1/ops/recovery/history/export-async/cancel-active`
  - Behavior:
    - default: cancel all active tasks
    - supports optional filters:
      - `exportType`
      - `status` (`QUEUED/RUNNING` only)
    - invalid/non-active `status` returns `400`
  - Added audit event:
    - `OPS_RECOVERY_HISTORY_EXPORT_ASYNC_CANCEL_ACTIVE`
  - Response:
    - `cancelledCount`
    - `remainingActiveCount`
    - `exportTypeFilter`
    - `statusFilter`
    - `message`

### 3) Backend tests
- Updated `ecm-core/src/test/java/com/ecm/core/controller/AnalyticsControllerTest.java`
  - Added coverage:
    - default `cancel-active` returns `200` and affects `activeCount`
    - `status=queued` returns `200`
    - invalid/non-active filter (for example `status=completed`) returns `400`
- Updated `ecm-core/src/test/java/com/ecm/core/controller/OpsRecoveryControllerSecurityTest.java`
  - Added coverage:
    - USER role: `403` on `cancel-active`
    - ADMIN role:
      - default `cancel-active` returns `200`
      - non-active `status=completed` returns `400`

## Impact
- 任务中心治理从“单条取消”升级为“批量取消活动任务”，减少高峰期人工逐条操作成本。
- 过滤器可控，避免误取消终态任务。

