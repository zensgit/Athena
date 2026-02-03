# Phase 17 - Permission Set Mapping (Dev) - 2026-02-03

## Goal
Provide explicit permission-set metadata (Coordinator/Editor/Contributor/Consumer) so the UI can render consistent labels and descriptions while keeping API compatibility.

## Scope
- Add labels/descriptions/order to `PermissionSet` enum.
- Expose metadata via a new API endpoint.
- Update Permissions dialog to consume metadata for preset selection.

## Implementation Notes
- Added `label`, `description`, and `order` fields to `PermissionSet`.
- Introduced `PermissionSetDto` for API responses.
- New endpoint: `GET /api/v1/security/permission-sets/metadata`.
- UI now displays human-friendly labels + descriptions and falls back to the legacy map if metadata is unavailable.

## Files Updated
- `ecm-core/src/main/java/com/ecm/core/entity/PermissionSet.java`
- `ecm-core/src/main/java/com/ecm/core/dto/PermissionSetDto.java`
- `ecm-core/src/main/java/com/ecm/core/controller/SecurityController.java`
- `ecm-frontend/src/services/nodeService.ts`
- `ecm-frontend/src/components/dialogs/PermissionsDialog.tsx`
