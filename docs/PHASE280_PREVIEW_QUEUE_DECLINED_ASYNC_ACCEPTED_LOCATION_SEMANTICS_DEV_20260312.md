# Phase 280 - Preview Queue Declined Async Accepted+Location Semantics (Dev)

## Date
- 2026-03-12

## Goal
- Standardize queue-declined async task creation/retry APIs to REST async semantics:
  - HTTP `202 Accepted`
  - `Location` response header for task tracking endpoint
- Keep existing response body fields (`taskId/status/deduplicated/...`) for frontend compatibility.

## Alfresco Benchmark Mapping
- Reference patterns:
  - `alfresco-community-repo/remote-api/src/main/java/org/alfresco/rest/api/downloads/DownloadsEntityResource.java`
  - `alfresco-community-repo/remote-api/src/main/java/org/alfresco/rest/api/nodes/NodeSizeDetailsRelation.java`
  - `alfresco-community-repo/remote-api/src/main/java/org/alfresco/rest/api/impl/DownloadsImpl.java`
- Borrowed ideas:
  - async create returns `202`
  - task status polling via dedicated GET endpoint
  - bulk operation async governance style
- Athena extension:
  - add explicit `Location` header consistently for both single-task and bulk-retry flows.

## Scope
- Backend API semantics (non-requeue + requeue dry-run async centers).
- Backend controller-security tests.
- Frontend mocked e2e route contracts for start/retry/retry-terminal APIs.

## Implementation
- `ecm-core/src/main/java/com/ecm/core/controller/PreviewDiagnosticsController.java`
  - Updated endpoints to return `202 Accepted` with `Location` header:
    - `POST /queue/declined/export-async`
    - `POST /queue/declined/export-async/{taskId}/retry`
    - `POST /queue/declined/export-async/retry-terminal`
    - `POST /queue/declined/export-async/retry-terminal/by-task-ids`
    - `POST /queue/declined/requeue/dry-run/export-async`
    - `POST /queue/declined/requeue/dry-run/export-async/{taskId}/retry`
    - `POST /queue/declined/requeue/dry-run/export-async/retry-terminal`
    - `POST /queue/declined/requeue/dry-run/export-async/retry-terminal/by-task-ids`
  - Added response helpers for accepted+location construction.
  - Added URI builders for task-status and task-list locations.
- `ecm-core/src/test/java/com/ecm/core/controller/PreviewDiagnosticsControllerSecurityTest.java`
  - Updated assertions from `isOk()` to `isAccepted()` on impacted start/retry endpoints.
  - Added `Location` existence assertions.
  - Updated async task-start helper methods to assert `202 + Location`.
- `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`
  - Updated mocked responses for impacted start/retry/retry-terminal endpoints from `200` to `202`.
  - Added `location` headers:
    - single-task operations point to `.../export-async/{taskId}`
    - bulk retry operations point to `.../export-async` list endpoints.

## Expected Outcomes
- API contract is closer to standard async REST behavior and Alfresco async semantics.
- Clients get explicit polling hints through `Location` while preserving current JSON body compatibility.
- Governance flows (dedup/retry/bulk retry) remain behaviorally unchanged.
