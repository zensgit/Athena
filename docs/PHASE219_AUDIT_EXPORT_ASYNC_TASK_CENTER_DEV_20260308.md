# Phase 219 - Audit Export Async Task Center - Development

## Date
2026-03-08

## Goal
- 为 Admin Dashboard 的审计导出提供异步任务中心能力（启动、列表、状态、取消、下载）。
- 与已落地的 Search/Preview/Ops async export 交互模型对齐，统一运维操作心智。

## Implemented

### 1) Backend async export task APIs
- Updated `ecm-core/src/main/java/com/ecm/core/controller/AnalyticsController.java`
  - Added endpoints:
    - `POST /api/v1/analytics/audit/export-async`
    - `GET /api/v1/analytics/audit/export-async`
    - `GET /api/v1/analytics/audit/export-async/{taskId}`
    - `POST /api/v1/analytics/audit/export-async/{taskId}/cancel`
    - `GET /api/v1/analytics/audit/export-async/{taskId}/download`
  - Added in-memory task registry and lifecycle:
    - `QUEUED` -> `RUNNING` -> `COMPLETED|FAILED|CANCELLED`
  - Reused existing audit export validation/range/preset/category logic for sync+async parity.

### 2) Backend test coverage
- Updated `ecm-core/src/test/java/com/ecm/core/controller/AnalyticsControllerTest.java`
  - Added async start/list happy-path assertions.
  - Added missing-task 404 checks for status/cancel/download endpoints.

### 3) Frontend task center integration
- Updated `ecm-frontend/src/pages/AdminDashboard.tsx`
  - Added async task state + request/response types for audit export.
  - Added async actions:
    - start task
    - refresh task list
    - cancel running task
    - download completed task
  - Added `Audit Async Export Tasks` table with row-level actions.
  - Refactored sync export and async export to share request-build logic.

### 4) Mocked E2E coverage
- Updated `ecm-frontend/e2e/admin-audit-filter-export.mock.spec.ts`
  - Added mocked routes for all async task center APIs.
  - Added UI flow assertions for:
    - start async task
    - refresh list
    - download completed task
    - cancel queued task

## Impact
- 审计导出从“同步阻塞下载”扩展为“可追踪任务中心”，对大窗口审计数据导出更稳健。
- 统一 async export 交互协议，为后续任务治理（配额、统一清理、队列可观测）提供可复用接口基线。
