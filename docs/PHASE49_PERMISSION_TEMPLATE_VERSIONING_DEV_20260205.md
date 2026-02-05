# Phase 49 - Permission Template Versioning (Dev)

## Goals
- Track permission template changes with immutable version snapshots.
- Allow admins to review template history and roll back safely.

## Backend Changes
- Added `permission_template_versions` table with FK to `permission_templates`.
- Implemented version snapshotting on create/update/rollback.
- Exposed history and rollback endpoints:
  - `GET /api/v1/security/permission-templates/{id}/versions`
  - `POST /api/v1/security/permission-templates/{id}/versions/{versionId}/rollback`

## Data Model
- `PermissionTemplateVersion`
  - `templateId`, `versionNumber`, `name`, `description`, `entries`
  - Audit fields via `BaseEntity`

## Frontend Changes
- Added history dialog in Permission Templates page.
- Added restore action per version.
- API wiring in `permissionTemplateService`.

## Notes
- Snapshotting copies entries to keep immutable history.
- Rollback creates a new version entry to preserve the rollback action.
