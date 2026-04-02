# Phase 221 - Audit Async Task Governance API - Development

## Date
2026-03-08

## Goal
- 为审计异步导出任务中心补齐治理接口：摘要统计 + 终态任务清理。
- 解决任务积累后的可观测性与可清理性问题，支撑长期运行。

## Implemented

### 1) Async task summary endpoint
- Updated `ecm-core/src/main/java/com/ecm/core/controller/AnalyticsController.java`
  - Added endpoint:
    - `GET /api/v1/analytics/audit/export-async/summary`
  - Returns:
    - `totalCount`
    - `queuedCount`
    - `runningCount`
    - `completedCount`
    - `cancelledCount`
    - `failedCount`
    - `activeCount`
    - `terminalCount`

### 2) Async task cleanup endpoint
- Updated `ecm-core/src/main/java/com/ecm/core/controller/AnalyticsController.java`
  - Added endpoint:
    - `POST /api/v1/analytics/audit/export-async/cleanup`
  - Behavior:
    - no `status`: cleanup all terminal tasks (`COMPLETED/CANCELLED/FAILED`)
    - with `status`: case-insensitive filter
    - rejects `QUEUED/RUNNING` as cleanup filter (`400`)
  - Response:
    - `deletedCount`
    - `remainingCount`
    - `statusFilter`
    - `message`

### 3) Controller tests
- Updated `ecm-core/src/test/java/com/ecm/core/controller/AnalyticsControllerTest.java`
  - Added summary counters test.
  - Added cleanup default behavior test.
  - Added cleanup `status=COMPLETED` selective deletion test.
  - Added cleanup `status=RUNNING` bad-request test.

## Impact
- 运维端可快速获知任务中心健康度（active/terminal 分布）。
- 提供受控清理能力，避免任务无限累积影响可读性和内存占用。
