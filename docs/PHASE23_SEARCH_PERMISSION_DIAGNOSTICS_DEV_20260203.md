# Phase 23 Search + Permission Diagnostics UI (2026-02-03)

## Goal
Expose search index stats and permission decision explanations directly in the UI to improve operational visibility and debugability.

## Changes
### Frontend
- Added search index stats retrieval and UI rendering in Search Results.
  - New API call to `/api/v1/search/index/stats`.
  - Access scope panel now shows index name, document count, and search enabled state for admins.
- Added permission diagnostics panel in Permissions dialog.
  - New API call to `/api/v1/security/nodes/{nodeId}/permission-diagnostics`.
  - Displays decision (Allowed/Denied), reason, dynamic authority, and matched allow/deny authorities.
  - Supports selecting permission type to diagnose.

### Service layer
- Added `getSearchIndexStats()` and `getPermissionDiagnostics()` in `nodeService` with typed responses.

## Files
- `ecm-frontend/src/services/nodeService.ts`
- `ecm-frontend/src/pages/SearchResults.tsx`
- `ecm-frontend/src/components/dialogs/PermissionsDialog.tsx`
- `ecm-frontend/e2e/search-view.spec.ts`
- `ecm-frontend/e2e/permissions-dialog.spec.ts`
