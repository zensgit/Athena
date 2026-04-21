# P5 PR-99 RM Report Preset Delivery UI Hardening

## Date
2026-04-21

## Scope

Review-driven hardening on top of shipped `PR-96/97/98`.

This slice does **not** add a new backend endpoint or a new frontend surface.
It closes two concrete correctness gaps in the scheduled-delivery UI chain:

1. summary-only presets were still exposing `Export CSV` from the preset table even though backend delivery/export only supports CSV-capable report kinds
2. `ScheduleReportPresetDialog` did not refresh schedule chips/history after `Save schedule` or `Deliver now`, so backend-updated `lastRunAt / nextRunAt / lastExecution` could stay stale until reopen

## Why now

`PR-96` fixed the frontend service contract shape and `PR-97/98` completed the first reachable UI path.
Once those pieces shipped, two review findings remained:

- table action semantics were still too broad for summary-only kinds
- delivery execution updated backend state, but the dialog only patched local list state instead of reloading authoritative status

These are small enough to land as one hardening slice instead of reopening the larger `PR-97/98` scope.

## Included

### 1. Preset table action gating

File:
- `ecm-frontend/src/pages/RecordsManagementPage.tsx`

Changes:
- `Export CSV` now only renders for `supportsReportPresetCsvDelivery(preset.kind)`
- `Schedule` remains gated by the same helper
- summary-only presets (`ACTIVITY_FAMILY_HIGHLIGHTS`, `ACTIVITY_FAMILY_MIX`) now stay audit-only in the preset table
- helper text above the preset table now states the intended split explicitly

### 2. Dialog refresh after save/deliver

File:
- `ecm-frontend/src/components/records/ScheduleReportPresetDialog.tsx`

Changes:
- `handleSave()` now reloads authoritative schedule state after a successful save
- `handleDeliverNow()` now reloads authoritative schedule state and recent executions after manual delivery
- local form fields are reset from returned backend state so canonicalized values are reflected immediately

### 3. Test coverage for shipped wiring

Files:
- `ecm-frontend/src/components/records/ScheduleReportPresetDialog.test.tsx`
- `ecm-frontend/src/pages/RecordsManagementPage.test.tsx`
- `ecm-frontend/src/services/recordsManagementService.test.ts`

Changes:
- dialog tests now cover refresh-after-save and refresh-after-deliver behavior
- page tests now cover the actual `PR-98` schedule button wiring
- page tests now assert summary-only presets stay audit-only in the preset table
- service tests now assert the kind guard behavior directly

## Design Notes

1. This slice keeps using the existing `supportsReportPresetCsvDelivery()` helper introduced in the `PR-96` fixup instead of creating a second “schedulable” ruleset in page code.
2. The preset table now matches backend truth:
   - CSV-capable report kinds: `Apply to audit`, `Export CSV`, `Schedule`
   - summary-only kinds: `Apply to audit` only
3. Dialog refresh is authoritative, not optimistic. After save/deliver, it re-reads backend schedule status instead of trying to mirror `lastRunAt / nextRunAt / lastExecution` locally.

## Non-goals

- no new backend behavior
- no folder picker upgrade
- no preset execution scheduler UI expansion beyond the existing dialog
- no Playwright/e2e coverage in this slice
