# P5 PR-103 RM Preset Delivery Ledger/Filter/Export API Development

## Scope

This slice converts the earlier PR-103 read-only plan into a **real backend foundation** for cross-preset delivery review.

Runtime changes in scope:

- owner-scoped execution ledger endpoint
- owner-scoped execution ledger CSV export
- additive preset metadata on execution rows
- repository expansion to support optional filtered queries
- controller and service tests for the new surface

Out of scope:

- frontend page-level ledger UI
- new table or migration
- replacing the existing per-preset `/{id}/executions?limit=N` history route

## Delivered API Surface

### Existing route kept as-is

- `GET /api/v1/records/report-presets/{id}/executions?limit=N`

This remains the lightweight per-preset history endpoint used by the shipped schedule dialog.

### New owner-scoped ledger route

- `GET /api/v1/records/report-presets/executions`

Supported query params:

- `presetId` optional `UUID`
- `status` optional `SUCCESS | FAILED`
- `triggerType` optional `MANUAL | SCHEDULED`
- `from` optional ISO-8601 datetime
- `to` optional ISO-8601 datetime
- `page` optional integer, default `0`
- `size` optional integer, default `20`, clamped to `1..100`

Returned JSON shape:

- `content[]`
- `page`
- `size`
- `totalElements`
- `totalPages`
- `first`
- `last`

Each row now includes additive preset metadata:

- `presetName`
- `presetKind`

### New CSV export route

- `GET /api/v1/records/report-presets/executions/export`

Supported query params:

- `presetId`
- `status`
- `triggerType`
- `from`
- `to`
- `limit` optional, default `200`, clamped to `1..1000`

CSV filename:

- `rm-report-preset-executions-YYYYMMDD-HHmmss.csv`

CSV columns:

- `executionId`
- `presetId`
- `presetName`
- `presetKind`
- `triggerType`
- `status`
- `filename`
- `targetFolderId`
- `documentId`
- `message`
- `startedAt`
- `finishedAt`
- `durationMs`

## Implementation Notes

### Repository

[RmReportPresetExecutionRepository.java](/Users/chouhua/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/repository/RmReportPresetExecutionRepository.java:1)

- expanded to `JpaSpecificationExecutor<RmReportPresetExecution>`
- existing per-preset finder methods were kept unchanged

### Service

[RmReportPresetDeliveryService.java](/Users/chouhua/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/RmReportPresetDeliveryService.java:1)

Added:

- `listExecutionLedger(...)`
- `exportExecutionLedgerCsv(...)`
- owner enforcement through `SecurityService.getCurrentUser()`
- optional `Specification`-based filtering
- page/size/limit normalization helpers
- execution-ledger CSV serialization helper

Also extended `PresetExecutionDto` with:

- `presetName`
- `presetKind`

That keeps dialog-local history and future page-level ledger consumption on one DTO shape.

### Controller

[RmReportPresetController.java](/Users/chouhua/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/controller/RmReportPresetController.java:1)

Added:

- `GET /executions`
- `GET /executions/export`

Controller-side hardening:

- returns a stable `ExecutionLedgerResponse` record instead of raw `Page`
- keeps CSV on a dedicated export path while reusing the same filter semantics

## Review Follow-up Applied During Implementation

Two issues surfaced while turning the earlier plan into executable code:

1. `MockMvc` JSON serialization of raw `Page` was not stable in this standalone controller test setup and raised `HttpMessageNotWritableException`.
   Fix: controller now maps the service `Page` into a stable `ExecutionLedgerResponse`.

2. Earlier controller tests still had an old `PresetExecutionDto` constructor shape and a brittle exact `Content-Type` assertion.
   Fix: controller tests now use the additive preset fields and assert CSV headers via `startsWith("text/csv")`.

I also tightened two service tests so `presetService.getOwned(presetId)` returns an object with the same `UUID` being filtered, instead of a helper-generated unrelated id.

CSV export should keep the same filter semantics as JSON. The route should not silently export a different dataset.

## Ownership and Security

The minimum safe rule is:

- ledger stays **owner-scoped**
- controller remains `@PreAuthorize("hasRole('ADMIN')")`
- query should filter on stored execution `owner`

This keeps behavior aligned with the rest of the preset surface and avoids reopening cross-user admin read semantics in the same slice.

## Minimal Persistence Follow-up

The existing indexes are:

- `(preset_id, started_at)`
- `(status)`

Those are not ideal for a cross-preset owner-scoped ledger.

The smallest useful migration for the runtime implementation would be:

- add `idx_rm_report_preset_exec_owner_started (owner, started_at)`

That supports the default ledger path and range scans without over-designing the table.

## Recommendation

The next highest-value slice should be:

`PR-103 backend ledger/filter/export foundation`

Not more schedule-dialog polish.

Reason:

- entity data already supports it
- existing controller/export style already defines the right pattern
- current frontend has enough management polish
- operator value now depends more on cross-preset review/export than on more dialog cosmetics

## Non-goals

- no runtime code in this review slice
- no frontend ledger UI yet
- no email/download-bundle delivery channel
- no cross-user super-admin ledger semantics
- no new evidence surface outside the preset workflow
