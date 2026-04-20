# P5 PR89 RM Report Preset Save-As-Preset UI Design

## Scope

This slice is the first frontend consumption of the `PR-83` RM report preset foundation.

It adds a reusable `Save as preset` affordance to existing RM report cards without changing backend contracts.

## Problem

`PR-83` shipped preset persistence and CRUD, but the RM page still had no direct way to save a report configuration from the cards that already expose:

- current-window actions
- previous-window actions
- CSV export actions
- audit drilldown actions

That left the preset foundation technically complete but operationally unreachable from the shipped RM UI.

## Design

Keep the slice frontend-only and reuse the existing preset API:

- `POST /api/v1/records/report-presets`

No new endpoint, migration, or execution surface is introduced.

### Shared preset dialog

Add a small reusable dialog:

- `SaveReportPresetDialog`

It captures:

- preset name
- optional description

It validates that name is non-empty and leaves request trimming to the existing service helper.

### Frontend data contract

Extend RM frontend types and service with:

- `RmReportPresetKind`
- `RmReportPreset`
- `createReportPreset(...)`

The service trims name/description before posting and keeps the payload aligned with the backend `PR-83` contract.

### RM page integration

Add a lightweight preset draft state to `RecordsManagementPage` and open the dialog from existing cards.

The page reuses the same window semantics already used by export and drilldown actions:

- named current/previous windows for highlight cards
- rolling current/previous windows for trend/report cards

Each card opens the dialog with:

- a prefilled display name
- helper text describing the effective window
- the correct preset `kind`
- the exact report `params` already implied by the card

### Included report cards

This slice adds save actions to these existing cards:

- `RM Contributor Family Highlights`
- `RM Contributor Event-Type Highlights`
- `RM Activity Family Highlights`
- `RM Activity Family Mix`
- `RM Activity Event Hotspots`
- `RM Activity Contributors`

For each card, both `Save current preset` and `Save previous preset` are exposed when the corresponding current/previous window semantics already exist.

### Intentional preset kind mapping

This slice saves **report/export-oriented** presets, not a second UI-only visualization contract.

That means cards such as:

- `RM Activity Family Highlights`
- `RM Activity Family Mix`

still persist the reusable `ACTIVITY_FAMILY_REPORT` contract, because the current user action is meant to preserve the same `from/to` report window that already backs CSV export and future report execution.

The same rule applies to other cards:

- highlights and mix cards may save a report-oriented preset kind
- no frontend-only preset kind execution surface is introduced in this slice

## Files

- `ecm-frontend/src/components/records/SaveReportPresetDialog.tsx`
- `ecm-frontend/src/pages/RecordsManagementPage.tsx`
- `ecm-frontend/src/services/recordsManagementService.ts`
- `ecm-frontend/src/services/recordsManagementService.test.ts`
- `ecm-frontend/src/types/index.ts`
- `ecm-frontend/src/pages/RecordsManagementPage.test.tsx`

## Acceptance

- RM report cards can save a reusable preset without leaving the page
- preset save uses the existing `PR-83` API and does not open a second execution surface
- current/previous save actions persist the same window semantics already used by CSV export and audit drilldown
- no backend API, migration, or policy behavior changes
