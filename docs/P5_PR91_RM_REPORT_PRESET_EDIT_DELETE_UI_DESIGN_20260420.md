# P5 PR91 RM Report Preset Edit-Delete UI Design

## Scope

This slice extends the shipped preset-consumption surface from `PR-90`.

It adds minimal preset management actions on top of the existing `Saved RM Report Presets` card:

- edit preset name/description
- delete preset

No new backend endpoint, migration, or preset execution contract is introduced.

## Problem

After `PR-89` and `PR-90`, the RM page could:

- save presets
- list presets
- apply presets to `Records Audit`
- export preset-backed CSVs

But operators still could not correct stale names/descriptions or remove obsolete presets from the same surface.

That left the preset workflow incomplete and forced direct API-only management for routine maintenance.

## Design

Keep the slice frontend-only and reuse the already shipped preset CRUD API from `PR-83`:

- `PUT /api/v1/records/report-presets/{id}`
- `DELETE /api/v1/records/report-presets/{id}`

### Edit flow

Reuse the existing `SaveReportPresetDialog` instead of introducing a second preset form.

The dialog is now parameterized with:

- `submitLabel`

That keeps create and edit on the same field contract:

- name
- optional description

Preset `kind` remains immutable and is not exposed for editing, matching the backend rule from `PR-83`.

### Delete flow

Delete is intentionally narrow:

- row-level button on the preset table
- browser confirm gate
- soft-delete through the existing backend API

This keeps the slice minimal while preserving the backend soft-delete semantics already established in `PR-83` and `PR-86`.

### Refresh behavior

After create, edit, or delete:

- the preset list is reloaded silently
- the rest of the RM page is not reloaded

That avoids unnecessary churn on audit and analytics cards.

## Files

- `ecm-frontend/src/components/records/SaveReportPresetDialog.tsx`
- `ecm-frontend/src/services/recordsManagementService.ts`
- `ecm-frontend/src/services/recordsManagementService.test.ts`
- `ecm-frontend/src/pages/RecordsManagementPage.tsx`
- `ecm-frontend/src/pages/RecordsManagementPage.test.tsx`

## Acceptance

- saved RM report presets can be renamed or have description updated from the RM page
- saved RM report presets can be deleted from the RM page
- edit/delete reuse the existing preset CRUD API
- no new backend protocol or execution surface is added
