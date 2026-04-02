# Phase 279 - Preview Queue Declined Async Task Center Standard Paging (Dev)

## Date
- 2026-03-12

## Goal
- Align queue-declined async task center list APIs with standard paging semantics:
  - `skipCount`
  - `maxItems`
  - `paging.totalItems`
  - `paging.hasMoreItems`
- Keep backward compatibility with existing `limit` usage.

## Alfresco Benchmark Mapping
- Reference:
  - `alfresco-community-repo/remote-api` list-style pagination conventions (`skipCount/maxItems`).
- Borrowed ideas:
  - machine-readable paging metadata for UI and automation.
- Athena extension:
  - applied to both queue-declined non-requeue and requeue-dry-run async task centers with unchanged task governance behavior.

## Scope
- Backend:
  - queue declined async list endpoint.
  - queue declined requeue dry-run async list endpoint.
  - list response DTOs upgraded with structured `paging` object.
- Frontend:
  - service request propagation for `skipCount/maxItems`.
  - task center page size selector + prev/next controls.
- Mocked e2e:
  - route handlers upgraded to parse `maxItems/skipCount` and return `paging` metadata.

## Implementation
- `ecm-core/src/main/java/com/ecm/core/controller/PreviewDiagnosticsController.java`
  - List endpoints now accept:
    - `maxItems` (preferred),
    - `limit` (compatibility alias),
    - `skipCount`.
  - Added compatibility helper `resolveListMaxItems(maxItems, limit, upperBound)`.
  - Reworked list builders to return paged result (`totalItems + paged items`) instead of truncating directly.
  - Added response DTO:
    - `PreviewTaskCenterPagingDto(skipCount, maxItems, totalItems, hasMoreItems)`.
  - Extended both list response DTOs to include `paging`.
- `ecm-core/src/test/java/com/ecm/core/controller/PreviewDiagnosticsControllerSecurityTest.java`
  - Added paging coverage:
    - `diagnosticsQueueDeclinedAsyncListSupportsPaging`
    - `diagnosticsQueueDeclinedRequeueDryRunAsyncListSupportsPaging`
  - Added helper methods for starting async tasks with unique query values to avoid dedup interference in paging assertions.
- `ecm-frontend/src/services/previewDiagnosticsService.ts`
  - Added `PreviewTaskCenterPaging` type.
  - Extended:
    - `PreviewQueueDeclinedExportTaskList`
    - `PreviewQueueDeclinedRequeueDryRunExportTaskList`
    with optional `paging`.
  - List methods now send `maxItems + skipCount` and preserve `limit=maxItems` compatibility.
- `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`
  - Added per-task-center paging state:
    - page,
    - page size (`10/20/50`),
    - total items,
    - hasMore.
  - List loading now uses `skipCount = page * maxItems`.
  - Added UI controls:
    - status filter resets page,
    - page-size selector,
    - page chips,
    - prev/next buttons.
  - Start/cleanup actions now reset to page 1 (`page=0`) before refresh.
- `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`
  - Async list mocks now parse `maxItems/skipCount`.
  - Responses now include `paging` object.
  - Call capture structures include `skipCount`.
  - Assertions updated to verify paging-enabled request pattern (`skipCount===0` baseline).

## Expected Outcomes
- Consistent task-center pagination contract for API/UI automation.
- Better large-task-list operability without changing retry/cancel/cleanup semantics.
- Backward-compatible rollout for existing `limit` callers.
