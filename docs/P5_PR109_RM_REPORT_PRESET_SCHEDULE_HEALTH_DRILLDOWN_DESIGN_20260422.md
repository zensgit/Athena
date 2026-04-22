# P5 PR-109 RM Report Preset Schedule Health Drilldown Design

## Scope

This slice extends the shipped preset scheduled-delivery chain with additive
schedule metadata on the existing preset list response, then consumes that data
in `RecordsManagementPage`.

Runtime changes in scope:

- additive schedule metadata on `GET /api/v1/records/report-presets`
- page-level preset table filter chips: `All`, `Scheduled`, `Due now`
- `Scheduled Delivery Health` drilldown into the preset table
- per-row delivery status in the preset table

Out of scope:

- new backend endpoint or migration
- email delivery channel
- replacing the per-preset schedule dialog

## Delivered Behavior

### Backend

The existing preset response now also exposes:

- `scheduleEnabled`
- `deliveryFolderId`
- `nextRunAt`
- `lastRunAt`

These are additive fields only; no route or authority model changed.

### Frontend

`Saved RM Report Presets` now has lightweight operational filtering:

- `All`
- `Scheduled`
- `Due now`

The table also shows delivery status inline:

- `Due now`
- `Scheduled`
- `Not scheduled`
- optional `Next ...`
- optional `Last ...`

`Scheduled Delivery Health` now drills into that same preset table:

- clicking `Scheduled presets: N` applies the scheduled filter
- clicking `Due now: N` applies the due-now filter

The drilldown intentionally stays on the current RM page and reuses the shipped
preset surface rather than creating a second management panel.

## Implementation Notes

### Backend controller

[RmReportPresetController.java](/Users/chouhua/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/controller/RmReportPresetController.java:1)

`ReportPresetResponse` was extended with additive schedule fields by mapping
directly from `RmReportPreset`.

No service/repository change was needed because the entity already carried the
required schedule metadata.

### Frontend types

[index.ts](/Users/chouhua/Downloads/Github/Athena/ecm-frontend/src/types/index.ts:1)

`RmReportPreset` now accepts the same additive schedule fields as optional
properties.

### Frontend page

[RecordsManagementPage.tsx](/Users/chouhua/Downloads/Github/Athena/ecm-frontend/src/pages/RecordsManagementPage.tsx:1)

Added:

- preset-table filter state
- due-now helper
- filter counts
- filtered preset dataset
- health-card drilldown helper
- table delivery-status column
- filtered empty-state copy

The drilldown helper uses optional `scrollIntoView?.(...)` so tests and
non-browser environments do not fail on missing DOM methods.

## Recommendation

The next highest-value slice should stay on this operational line:

- page-level scheduled delivery management polish
- or one higher-level preset schedule/admin E2E around the new drilldown path

This is still lower risk than opening the separate email-delivery capability.
