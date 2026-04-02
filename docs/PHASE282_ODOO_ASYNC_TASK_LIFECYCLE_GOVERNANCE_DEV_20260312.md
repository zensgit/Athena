# Phase 282 - Odoo Async Task Lifecycle Governance Parity (Dev)

## Date
- 2026-03-12

## Goal
- Extend async task governance from queue-declined task centers to:
  - preview rendition resources async export center
  - ops recovery history async export center
- Align Athena lifecycle governance with Odoo-style operational patterns (explicit lifecycle states, timeout/retention transitions, actor traceability), then keep Athena’s stronger admin diagnostics UI.

## Odoo Benchmark Mapping
- Odoo 18 reference (`/Users/huazhou/Downloads/Github/Yuantus/references/odoo18-enterprise-main`) emphasizes long-running job lifecycle visibility and terminal-state governance.
- Athena Phase282 parity/surpass points:
  - Added lifecycle terminal states `TIMED_OUT/EXPIRED` for two additional async centers.
  - Added per-task lifecycle metadata (`startedAt/updatedAt/timeoutAt/expiresAt/createdBy/updatedBy`) in API + UI.
  - Added server-side lifecycle refresh pass (active timeout + terminal expiry) on list/summary/get/cleanup/cancel/download flows.

## Scope
- Backend:
  - `ecm-core/src/main/java/com/ecm/core/controller/PreviewDiagnosticsController.java`
  - `ecm-core/src/main/java/com/ecm/core/controller/OpsRecoveryController.java`
  - `ecm-core/src/test/java/com/ecm/core/controller/PreviewDiagnosticsControllerSecurityTest.java`
- Frontend:
  - `ecm-frontend/src/services/previewDiagnosticsService.ts`
  - `ecm-frontend/src/services/opsRecoveryService.ts`
  - `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`

## Implementation
- Preview diagnostics rendition async task center:
  - State machine extended: `QUEUED/RUNNING/COMPLETED/CANCELLED/FAILED/TIMED_OUT/EXPIRED`.
  - Task record extended with lifecycle/audit fields and actor-aware transitions.
  - Added lifecycle refresh methods:
    - `refreshRenditionResourcesExportAsyncTasksLifecycleLocked`
    - `applyRenditionResourcesExportAsyncTaskLifecycle`
  - Applied lifecycle refresh in list/summary/cleanup/cancel-active/get/cancel/download.
  - Summary DTO extended with `timedOutCount/expiredCount`.
  - Create/status DTOs extended with timeout/expiry/actor metadata.
  - Cleanup terminal-filter validation expanded to include `TIMED_OUT, EXPIRED`.

- Ops recovery history async export task center:
  - State machine extended: `QUEUED/RUNNING/COMPLETED/CANCELLED/FAILED/TIMED_OUT/EXPIRED`.
  - Task record extended with lifecycle/audit fields and actor-aware transitions.
  - Added lifecycle constants and helper methods:
    - active timeout and retention expiry
    - `resolveAuditUsername`, `resolveTaskActor`, timeout/expiry resolvers
  - Added lifecycle refresh methods:
    - `refreshHistoryExportAsyncTasksLifecycleLocked`
    - `applyHistoryExportAsyncTaskLifecycle`
  - Applied lifecycle refresh in list/summary/cleanup/cancel-active/get/cancel/download/count-active.
  - Summary DTO extended with `timedOutCount/expiredCount`.
  - Create/status DTOs extended with timeout/expiry/actor metadata.

- Frontend task-center contract + UI alignment:
  - Rendition async task types/status filters updated to include lifecycle fields and `TIMED_OUT/EXPIRED`.
  - Ops recovery async task types/status filters updated to include lifecycle fields and `TIMED_OUT/EXPIRED`.
  - Preview Diagnostics UI:
    - status filters add `Timed out` and `Expired`
    - summary chips add timed-out/expired counters
    - task rows show `Updated/Timeout/Expires` + actor metadata.

## Compatibility
- Existing endpoints remain path-compatible.
- New response fields are additive and backward compatible.
- Existing status filters remain valid; new statuses are optional extensions.
