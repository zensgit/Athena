# Phase 21 Permission Diagnostics (2026-02-03)

## Goal
Expose a permission diagnostics endpoint that explains why the current user is granted or denied a specific permission (admin/owner/dynamic authority/ACL allow/deny).

## Changes
### Backend
- Added `PermissionDecision` diagnostic response model to `SecurityService`.
- Added `explainPermission` with detailed reasoning (admin/owner/dynamic authority/ACL allow/deny/no match).
- Added new endpoint `GET /api/v1/security/nodes/{nodeId}/permission-diagnostics`.

Files:
- `ecm-core/src/main/java/com/ecm/core/service/SecurityService.java`
- `ecm-core/src/main/java/com/ecm/core/controller/SecurityController.java`

## Notes
- Requires READ permission to view diagnostics (same check used for listing permissions).
- Uses current authenticated user for the evaluation.
