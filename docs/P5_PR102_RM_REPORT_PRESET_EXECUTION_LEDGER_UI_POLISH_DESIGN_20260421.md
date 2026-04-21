# P5 PR-102 RM Report Preset Execution Ledger UI Polish

## Date
2026-04-21

## Scope

Polish the shipped preset scheduled-delivery operator surface without changing backend protocol:

- improve execution ledger usability inside `Schedule Delivery`
- add local execution filtering
- add direct navigation to delivered document / target folder
- expose a stronger current-schedule summary for the last execution result

This slice does **not** add a new backend endpoint, table, or migration.

## Why now

By `PR-101`, the preset scheduled-delivery chain already had:

- backend schedule/delivery/ledger foundation
- frontend service layer
- dialog/page wiring
- UI hardening
- mocked E2E
- one real full-stack smoke

What was still thin was the operator evidence surface:

- execution history could not be filtered
- delivered artifacts could not be opened directly
- current schedule summary did not expose the last execution result

That made the feature technically complete but still weaker than it needed to be for day-to-day admin use.

## Included

### 1. Execution ledger filters

File:
- `ecm-frontend/src/components/records/ScheduleReportPresetDialog.tsx`

Add local filters for:

- result: `All results / Successful / Failed`
- trigger: `All triggers / Manual / Scheduled`

The dialog now also shows:

- `Showing X of Y deliveries`
- an empty-state message when the current filters remove all rows

### 2. Delivery navigation

The dialog now exposes direct browse navigation for execution evidence:

- `Open delivered file` when `documentId` exists
- `Open target folder` when `targetFolderId` exists
- `Open target folder` in the current-schedule summary when the preset has a configured delivery folder

Navigation intentionally reuses the existing `/browse/{nodeId}` surface instead of introducing a second delivery evidence page.

### 3. Current schedule summary polish

The current-schedule block now includes:

- `Enabled / Disabled`
- `Next`
- `Last`
- `Last Result`

`Last Result` comes from the already shipped `lastExecution` field on `RmReportPresetScheduleStatus`.

## Non-goals

- no per-row preset table schedule summary fetch
- no new backend ledger filter/export API
- no scheduled-delivery email/download-bundle channel
- no new page-level preset management surface
