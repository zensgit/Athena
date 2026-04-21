# P5 PR-104 RM Report Preset Execution Ledger UI Design

## Scope

This slice turns the shipped `PR-103` cross-preset execution-ledger API into a
page-level operator surface on `RecordsManagementPage`.

Runtime changes in scope:

- page-level preset delivery ledger card
- owner-scoped filter form for preset/result/trigger/date range
- CSV export reuse against the shipped backend ledger export route
- browse/apply actions from ledger rows back into existing preset and evidence flows
- frontend types and service methods for the new ledger JSON/CSV surface

Out of scope:

- new backend endpoint or migration
- replacing the existing per-preset schedule dialog history
- new evidence surface outside `Records Audit` and existing browse routes

## Delivered UI Surface

### New page-level card

`RecordsManagementPage` now renders a `Preset Delivery Ledger` card beside the
existing preset-management surface.

The card includes:

- preset selector
- result selector
- trigger selector
- `from` / `to` datetime filters
- `Apply`
- `Clear`
- `Export ledger CSV`
- result summary: `Showing X of Y deliveries`

### Execution table

Each ledger row shows:

- started time
- preset name
- trigger type
- result status
- delivered filename
- message
- actions

Row actions:

- `Apply preset`
- `Open delivered file`
- `Open target folder`

### Export semantics

CSV export reuses `GET /api/v1/records/report-presets/executions/export`.

The UI exports:

- the currently applied filters
- trimmed `from` / `to`
- a `limit` equal to `max(totalElements, currentPageSize)`

That keeps the export bounded while ensuring it does not silently truncate below
the current paginated surface size.

## Frontend Implementation

### Types

[index.ts](/Users/chouhua/Downloads/Github/Athena/ecm-frontend/src/types/index.ts:1)

Extended `RmReportPresetExecution` with additive preset metadata:

- `presetName`
- `presetKind`

### Service

[recordsManagementService.ts](/Users/chouhua/Downloads/Github/Athena/ecm-frontend/src/services/recordsManagementService.ts:1)

Added:

- `listReportPresetExecutionLedger(filters)`
- `exportReportPresetExecutionLedgerCsv(filters)`
- page-level filter/export types

The service normalizes:

- empty optional filters
- trimmed `presetId/from/to`
- backend pagination shape into the existing frontend `PageResponse`
- export filename range from normalized dates

### Page

[RecordsManagementPage.tsx](/Users/chouhua/Downloads/Github/Athena/ecm-frontend/src/pages/RecordsManagementPage.tsx:1)

Added:

- ledger page state
- applied vs editing filter state
- page index / rows per page state
- error and exporting states
- filter apply/clear handlers
- page-level load effect
- export handler
- browse helpers for delivered file / target folder

The page intentionally reuses existing flows:

- `Apply preset` goes back through the shipped preset apply path
- browse actions stay on `/browse/{nodeId}`
- no new dialog or secondary report engine is introduced

## Review Notes

The only correctness mismatch found during implementation was test-side, not
runtime-side:

- the UI normalizes datetime-local values to the browser-submitted strings
  actually used by the filter/export calls
- export uses `max(totalElements, currentPageSize)` rather than exact
  `totalElements`

The shipped runtime behavior is internally consistent, so the follow-up was to
align the test and documentation rather than changing the export contract again.

## Recommendation

The next highest-value slice should be:

- page-level operator polish on top of this ledger surface

Not more new backend protocol.

The backend foundation now exists; the main value is on operational consumption.
