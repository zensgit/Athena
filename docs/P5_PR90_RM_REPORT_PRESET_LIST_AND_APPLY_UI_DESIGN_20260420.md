# P5 PR90 RM Report Preset List and Apply UI Design

## Scope

This slice is the second frontend consumption of the `PR-83` RM report preset foundation.

It adds a small `Saved RM Report Presets` card to `RecordsManagementPage` and keeps all actions inside already-shipped surfaces:

- `Records Audit`
- existing RM CSV export routes

No new backend endpoint, migration, or preset execution surface is introduced.

## Problem

`PR-89` added `Save as preset`, but the page still had no way to:

- see saved presets
- reuse a saved preset
- route a saved preset into existing evidence/export surfaces

That meant presets could be created but not meaningfully consumed from the shipped RM UI.

## Design

Keep the slice frontend-only and reuse existing backend contracts:

- `GET /api/v1/records/report-presets`
- existing report CSV endpoints

### Preset list

Add `listReportPresets()` to `recordsManagementService` and load presets independently from the rest of the RM page.

The list is isolated from the main RM summary/analytics loads:

- a preset-list failure does not block the rest of the page
- a successful preset save silently refreshes the list

### Preset card

Render a single `Saved RM Report Presets` card with:

- preset name
- kind
- date window derived from `params.from` / `params.to`
- description
- last updated timestamp

### Apply semantics

`Apply to audit` is intentionally narrow:

- it reuses the existing `Records Audit` table
- it sets `from/to`
- it also applies `family`, `eventType`, and `username` if those params are present

This keeps presets inside the primary RM evidence surface instead of inventing a second execution UI.

### Export semantics

`Export CSV` reuses existing report export routes by mapping preset `kind` to the corresponding shipped CSV method:

- `ACTIVITY_FAMILY_REPORT`
- `ACTIVITY_FAMILY_HIGHLIGHTS`
- `ACTIVITY_FAMILY_MIX`
  - all map to existing family-report CSV export
- `ACTIVITY_EVENT_TYPE_REPORT`
- `ACTIVITY_CONTRIBUTOR_REPORT`
- `ACTIVITY_CONTRIBUTOR_FAMILY_REPORT`
- `ACTIVITY_CONTRIBUTOR_EVENT_TYPE_REPORT`

This is still not a generic preset execution engine. It is a thin frontend reuse layer over already-shipped export contracts.

## Files

- `ecm-frontend/src/services/recordsManagementService.ts`
- `ecm-frontend/src/services/recordsManagementService.test.ts`
- `ecm-frontend/src/pages/RecordsManagementPage.tsx`
- `ecm-frontend/src/pages/RecordsManagementPage.test.tsx`

## Acceptance

- RM page shows saved report presets from the existing preset API
- a saved preset can be applied to the existing `Records Audit` filters
- a saved preset can trigger the corresponding existing CSV export path
- no new backend protocol or preset execution surface is added
