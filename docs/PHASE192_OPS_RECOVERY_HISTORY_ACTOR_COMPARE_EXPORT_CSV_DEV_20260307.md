# Phase 192 - Ops Recovery History Actor Compare CSV Export (Development)

## Date
2026-03-07

## Goal
- Complete actor-compare flow with CSV export.
- Keep actor-compare export aligned with active filters, sort, and TopN settings.
- Expose export action in diagnostics UI and verify end-to-end propagation.

## Implemented

### 1) Backend actor-compare export API
- Updated `ecm-core/src/main/java/com/ecm/core/controller/OpsRecoveryController.java`:
  - Added endpoint:
    - `GET /api/v1/ops/recovery/history/summary/compare/actors/export`
  - Reuses:
    - `queryHistorySummaryCompareActors(days, mode, actor, eventType, limit, sort)`
  - Added CSV builder:
    - `buildHistoryCompareActorsCsv(compareActors)`
  - Added audit event:
    - `OPS_RECOVERY_HISTORY_ACTOR_COMPARE_EXPORT`
    - helper: `auditHistoryCompareActorsExport(...)`
  - Added response header:
    - `X-Ops-Recovery-Compare-Actors-Count`

### 2) Backend tests
- Updated `ecm-core/src/test/java/com/ecm/core/controller/OpsRecoveryControllerSecurityTest.java`:
  - Added forbidden check for actor-compare export endpoint.
  - Added admin export success test:
    - validates content-type + header
    - validates CSV includes sort/limit metadata and actor rows.

### 3) Frontend integration
- Updated `ecm-frontend/src/services/opsRecoveryService.ts`:
  - Added event type:
    - `OPS_RECOVERY_HISTORY_ACTOR_COMPARE_EXPORT`
  - Added API:
    - `exportHistorySummaryCompareActorsCsv(days, mode, actor, eventType, limit, sort)`
- Updated `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`:
  - Added action button:
    - `Export Actor Compare CSV`
  - Added export handler + toasts:
    - success: `Ops recovery history actor compare CSV exported`
    - failure: `Failed to export ops recovery history actor compare CSV`
  - Added event type filter option:
    - `History Actor Compare Export Event`

### 4) Mock E2E alignment
- Updated `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`:
  - Added mock route:
    - `/api/v1/ops/recovery/history/summary/compare/actors/export`
  - Added UI click + success toast assertion for new actor compare export button.
  - Added request propagation assertions for actor compare export (`days/mode/actor/eventType/limit/sort`).
