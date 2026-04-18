# PR-21B RM Operations UI Design

## Scope

`PR-21B` completes the front-end half of the RM operations telemetry work introduced in `PR-21A`.

This slice adds a dedicated operations section inside the existing Records Management admin page and reuses the already-authoritative backend endpoint:

- `GET /api/v1/records/operations`

It does not introduce new RM policy semantics and does not attempt `undeclare`, import-create guards, or transfer-create guards.

## Design Choices

### 1. Keep RM operations on the existing admin page

The UI extends `RecordsManagementPage` instead of adding a separate dashboard route.

Reasons:

- keeps RM governance surfaces in one admin entry point
- reuses existing page load and refresh behavior
- avoids another navigation surface for a still-narrow telemetry feature

### 2. Use backend telemetry as the source of truth

The page does not derive governance state client-side.

It consumes:

- governed import job counts
- governed transfer job counts
- import status breakdown
- transfer status breakdown
- recent governed import jobs
- recent governed transfer jobs

### 3. Keep failure isolation local to telemetry

Operations telemetry is loaded separately from summary/file-plan/category/record data.

If `/records/operations` fails:

- the rest of the RM admin page remains usable
- the operations section shows a warning state
- refresh continues to reload the section independently

## Front-End Changes

### Types

Added DTOs in `ecm-frontend/src/types/index.ts`:

- `GovernedImportJob`
- `GovernedTransferJob`
- `RecordsOperationsTelemetry`

### Service

Added `recordsManagementService.getOperationsTelemetry(limit?: number)` in:

- `ecm-frontend/src/services/recordsManagementService.ts`

### Page

Extended:

- `ecm-frontend/src/pages/RecordsManagementPage.tsx`

New UI blocks:

- four operations metric cards
- import status breakdown chips
- transfer status breakdown chips
- recent governed imports table
- recent governed transfers table

## Deferred

Still deferred after `PR-21B`:

- undeclare/release workflow
- deeper create-path RM guardrails for bulk import and transfer
- standalone RM operations dashboard or richer charting
