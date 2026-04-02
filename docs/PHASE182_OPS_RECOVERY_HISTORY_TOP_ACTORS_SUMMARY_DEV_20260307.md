# Phase 182 - Ops Recovery History Top Actors Summary (Development)

## Date
2026-03-07

## Goal
- Extend recovery history summary with actor-level aggregation.
- Provide fast visibility into who is driving the highest recovery activity.

## Implemented

### 1) Backend: actor aggregation query
- Updated `ecm-core/src/main/java/com/ecm/core/repository/AuditLogRepository.java`:
  - Added `countByUsernamePrefixWithFilters(...)` grouped query.
  - Filter semantics match history summary/list/export:
    - event prefix
    - days window cutoff
    - actor filter
    - exact event type filter

### 2) Backend: summary payload extension
- Updated `ecm-core/src/main/java/com/ecm/core/controller/OpsRecoveryController.java`:
  - `GET /api/v1/ops/recovery/history/summary` now returns:
    - existing `items` (event/mode grouped)
    - new `actorItems` (actor grouped)
  - Added DTO `RecoveryHistoryActorSummaryItemDto`.
  - Added mapper `toRecoveryHistoryActorSummaryItem(...)`.
  - Added null-safe handling for repository grouped query results.

### 3) Backend tests
- Updated `ecm-core/src/test/java/com/ecm/core/controller/OpsRecoveryControllerSecurityTest.java`:
  - Extended summary test to mock actor grouped query.
  - Added assertions for `actorItems`.

### 4) Frontend service and UI
- Updated `ecm-frontend/src/services/opsRecoveryService.ts`:
  - Added:
    - `RecoveryHistoryActorSummaryItem`
    - `actorItems` field on `RecoveryHistorySummaryResult`
- Updated `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`:
  - Added actor summary state.
  - Added UI chips:
    - `Top actor <name> <count>`
  - Keeps existing summary and list/export filter alignment.

### 5) Mock E2E alignment
- Updated `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`:
  - Summary mock now returns `actorItems`.
  - Added UI assertion for top-actor chip.
  - Added strict text matcher fix for `Actor admin` chip assertion.
