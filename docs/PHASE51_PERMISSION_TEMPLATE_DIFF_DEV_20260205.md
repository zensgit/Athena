# Phase 51 - Permission Template Version Diff (Dev)

## Goals
- Provide a compare view for permission template versions to see entry changes.

## Backend Changes
- Added version detail endpoint:
  - `GET /api/v1/security/permission-templates/{id}/versions/{versionId}`
- Added `PermissionTemplateVersionDetailDto` for full entry payload.

## Frontend Changes
- Permission template history now includes **Compare** action.
- Comparison dialog shows change summary chips and a detailed diff table.

## Files Updated
- `ecm-core/src/main/java/com/ecm/core/controller/PermissionTemplateController.java`
- `ecm-core/src/main/java/com/ecm/core/service/PermissionTemplateService.java`
- `ecm-core/src/main/java/com/ecm/core/dto/PermissionTemplateVersionDetailDto.java`
- `ecm-frontend/src/pages/PermissionTemplatesPage.tsx`
- `ecm-frontend/src/services/permissionTemplateService.ts`
- `ecm-frontend/e2e/permission-templates.spec.ts`
