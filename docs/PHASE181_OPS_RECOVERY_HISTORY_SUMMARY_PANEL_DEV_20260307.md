# Phase 181 - Ops Recovery History Summary Panel (Development)

## Date
2026-03-07

## Goal
- Give operators a fast grouped view of recovery activity without scanning full history rows.
- Keep summary filters aligned with history list/export semantics.

## Implemented

### 1) Backend summary API
- Updated `ecm-core/src/main/java/com/ecm/core/repository/AuditLogRepository.java`:
  - Added `countByEventTypePrefixWithFilters(...)` grouped count query with filters:
    - event prefix
    - window start time
    - actor
    - exact event type
- Updated `ecm-core/src/main/java/com/ecm/core/controller/OpsRecoveryController.java`:
  - Added `GET /api/v1/ops/recovery/history/summary`.
  - Supports filters:
    - `days`
    - `mode`
    - `actor`
    - `eventType`
  - Added exact-event resolution helper (`eventType` takes precedence, then `mode`).
  - Added summary DTOs:
    - `RecoveryHistorySummaryResponseDto`
    - `RecoveryHistorySummaryItemDto`

### 2) Backend tests
- Updated `ecm-core/src/test/java/com/ecm/core/controller/OpsRecoveryControllerSecurityTest.java`:
  - Added non-admin forbidden check for `/history/summary`.
  - Added admin summary test with mode+actor filters and grouped count assertions.

### 3) Frontend summary integration
- Updated `ecm-frontend/src/services/opsRecoveryService.ts`:
  - Added types:
    - `RecoveryHistorySummaryItem`
    - `RecoveryHistorySummaryResult`
  - Added `getHistorySummary(days, mode?, actor?, eventType?)`.
- Updated `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`:
  - Added summary state and query in load pipeline.
  - Added summary chip row in **Ops Recovery Execution History** panel:
    - total count chip
    - top grouped mode/event chips with tooltip showing event type

### 4) Mock E2E coverage
- Updated `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`:
  - Added `/history/summary` branch in existing recovery-history mock route.
  - Added summary-call tracking and assertions for days/mode/actor/eventType propagation.
  - Added UI assertions for summary chips.
