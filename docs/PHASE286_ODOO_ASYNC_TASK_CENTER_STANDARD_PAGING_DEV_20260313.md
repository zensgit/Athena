# Phase 286 - Odoo Async Task Center Standard Paging (Dev)

## Date
- 2026-03-13

## Goal
- Complete Odoo-style async task-center paging parity for:
  - preview rendition resources async export center
  - ops recovery history async export center
- Standardize list contract to `maxItems + skipCount + paging` while keeping legacy `limit` compatibility.
- Add frontend page-size and page-navigation controls to support large async task sets.

## Odoo Benchmark Mapping
- Odoo job/task centers emphasize stable, cursor-like pagination semantics and operator-friendly page controls.
- Athena Phase286 parity/surpass points:
  - backend list APIs support explicit `maxItems/skipCount` and return structured paging metadata.
  - frontend exposes page size, listed/total counters, prev/next controls, and page overflow self-heal.

## Scope
- Backend:
  - `ecm-core/src/main/java/com/ecm/core/controller/PreviewDiagnosticsController.java`
  - `ecm-core/src/main/java/com/ecm/core/controller/OpsRecoveryController.java`
  - `ecm-core/src/test/java/com/ecm/core/controller/PreviewDiagnosticsControllerSecurityTest.java`
  - `ecm-core/src/test/java/com/ecm/core/controller/OpsRecoveryControllerSecurityTest.java`
- Frontend:
  - `ecm-frontend/src/services/previewDiagnosticsService.ts`
  - `ecm-frontend/src/services/opsRecoveryService.ts`
  - `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`

## Implementation
- Preview rendition async list API:
  - endpoint: `GET /api/v1/preview/diagnostics/renditions/resources/export-async`
  - supports:
    - `maxItems` (preferred)
    - `skipCount` (preferred)
    - `limit` (compatibility alias)
    - `status` filter
  - response adds:
    - `paging.skipCount`
    - `paging.maxItems`
    - `paging.totalItems`
    - `paging.hasMoreItems`
- Ops recovery history async list API:
  - endpoint: `GET /api/v1/ops/recovery/history/export-async`
  - supports:
    - `maxItems` (preferred)
    - `skipCount` (preferred)
    - `limit` (compatibility alias)
    - `exportType` and `status` filters
  - response adds the same `paging` contract.
- Frontend services:
  - preview service list method now accepts `(maxItems, status, skipCount)` and sends both `maxItems` and `limit=maxItems`.
  - ops service list method now accepts `(maxItems, exportType, status, skipCount)` and sends both `maxItems` and `limit=maxItems`.
- Frontend task-center UI:
  - rendition center adds:
    - page-size selector (`Rows 10/20/50`)
    - page chip (`Page x/y`)
    - listed chip (`Listed n/total`)
    - prev/next page controls
  - ops center adds the same controls.
  - both centers reset to page 1 on filter/page-size changes.
  - both centers auto-step back one page when current page becomes empty but total still has rows (post-cleanup/terminal transitions).

## Compatibility
- Backward compatible:
  - legacy clients using `limit` continue to work.
  - new `paging` field is additive.
  - existing async task actions (start/retry/cancel/download/cleanup) remain unchanged.
