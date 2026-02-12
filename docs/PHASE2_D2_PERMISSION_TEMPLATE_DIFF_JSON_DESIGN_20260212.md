# Phase 2 Day 2 (P0) - Permission Template Diff Export JSON + Audit (Design)

Date: 2026-02-12

## Goal

When an admin compares two permission template versions, they should be able to export the diff as:
- CSV (existing UX)
- JSON (new)

Export actions must also write a Security-category audit event so administrators can trace:
- who exported
- which template
- which versions
- which format

## Scope

- Backend: `ecm-core`
- Frontend: `ecm-frontend`
- Automation: Playwright E2E (existing permission templates spec extended)

Out of scope:
- Changing the diff semantics shown in the UI table (kept identical to current behavior).
- Adding additional permission flags (templates only store `permissionSet` per authority identity).

## Backend Changes

### New Endpoint

Add an export endpoint that supports both formats:

`GET /api/v1/security/permission-templates/{id}/versions/diff/export?from={fromVersionId}&to={toVersionId}&format=csv|json`

Behavior:
- Validates `{id}` exists and the two versions belong to the template.
- Computes diff using the same identity semantics as the UI:
  - identity key = `authorityType + ":" + authority`
  - `added` if identity only exists in `to`
  - `removed` if identity only exists in `from`
  - `changed` if identity exists in both and `permissionSet` differs
- `format=json`: returns `PermissionTemplateVersionDiffDto` JSON
- `format=csv`: returns a CSV (header + diff rows)

### Audit Event

On successful export, log:
- `eventType`: `SECURITY_PERMISSION_TEMPLATE_DIFF_EXPORT`
- `nodeId`: templateId
- `nodeName`: templateName
- `username`: `SecurityService.getCurrentUser()`
- `details`: includes from/to version ids + numbers, format, and counts

Reason: Audit category resolution relies on the `SECURITY_` prefix.

### Files

- `ecm-core/src/main/java/com/ecm/core/controller/PermissionTemplateController.java`
- `ecm-core/src/main/java/com/ecm/core/service/PermissionTemplateService.java`
- `ecm-core/src/main/java/com/ecm/core/dto/PermissionTemplateVersionDiffDto.java`

## Frontend Changes

### Compare Dialog Export Buttons

In `PermissionTemplatesPage` compare dialog:
- Keep `Export CSV`, but route it through the backend export endpoint (so audit always fires).
- Add `Export JSON` next to it.
- Both buttons:
  - disabled if versions not loaded
  - disabled while an export is in progress
  - download a meaningful filename:
    - `{templateName}-diff-{fromVersionNumber}-to-{toVersionNumber}.csv`
    - `{templateName}-diff-{fromVersionNumber}-to-{toVersionNumber}.json`

### API Client

Add `permissionTemplateService.exportVersionDiff(...)` that downloads a `Blob` from the backend export endpoint.

### Files

- `ecm-frontend/src/pages/PermissionTemplatesPage.tsx`
- `ecm-frontend/src/services/permissionTemplateService.ts`

## Automated Verification

Backend unit tests:
- Service diff logic:
  - detects `changed` when the same authority identity changes `permissionSet`
  - CSV formatter includes header + expected row
- Controller unit:
  - `format=json` returns DTO and logs audit
  - `format=csv` returns bytes and logs audit

Playwright E2E:
- Extend `e2e/permission-templates.spec.ts`:
  - export CSV (existing)
  - export JSON and assert the downloaded JSON contains `added`, `removed`, `changed` arrays

## Compatibility / Migration

- No schema change.
- Backward compatible UI:
  - Comparison table remains client-computed from loaded version details.
  - Export paths are additive.
- No secrets added.

