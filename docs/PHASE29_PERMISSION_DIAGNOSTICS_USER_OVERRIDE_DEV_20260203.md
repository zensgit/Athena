# Phase 29 Permission Diagnostics User Override (2026-02-03)

## Goal
Allow admins to diagnose permissions for a specific username in the Permissions dialog.

## Changes
### Backend
- Added optional `username` parameter to `GET /api/v1/security/nodes/{nodeId}/permission-diagnostics`.
- Enforced admin-only access when diagnosing other users.

### Frontend
- Added **Diagnose as** field in Permissions dialog (admin only).
- Wired diagnostics requests to pass the selected username.

## Files
- `ecm-core/src/main/java/com/ecm/core/controller/SecurityController.java`
- `ecm-frontend/src/services/nodeService.ts`
- `ecm-frontend/src/components/dialogs/PermissionsDialog.tsx`
- `ecm-frontend/e2e/permissions-dialog.spec.ts`
