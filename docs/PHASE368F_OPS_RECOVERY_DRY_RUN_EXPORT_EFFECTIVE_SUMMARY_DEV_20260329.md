# PHASE368F Ops Recovery Dry-Run Export Effective Summary

## Goal

Continue the `preview / rendition / search` source-of-truth line by extending `Ops Recovery Dry-Run` from an in-page planning surface into an exportable operator artifact.

This phase adds:

- a dedicated `dry-run/export` CSV endpoint
- shared dry-run computation between JSON and CSV paths
- effective preview summary columns in the exported samples
- a matching `Export Dry-run CSV` action in `PreviewDiagnosticsPage`

## Why This Phase

After the previous phase, Athena’s `Ops Recovery Dry-Run` JSON response already carried:

- `previewStatus`
- `previewFailureReason`
- `previewFailureCategory`
- `previewLastUpdated`

But operators still had no direct way to export that richer plan. The only export surfaces in `OpsRecovery` were:

- history exports
- history summary/trend/compare exports
- async retry-terminal dry-run export

That meant the main recovery planner remained one step behind:

- the UI could display richer dry-run samples
- but exported evidence still required screenshots or manual copying

## Scope

### Backend

Added `POST /api/v1/ops/recovery/dry-run/export` in [OpsRecoveryController.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/controller/OpsRecoveryController.java).

Key changes:

- Extracted a shared `computeDryRun(...)` helper so JSON dry-run and CSV dry-run export use the same planning logic.
- Added `buildRecoveryDryRunCsv(...)` to serialize the dry-run plan with effective preview summary fields.
- Added `auditDryRunExport(...)` so export is logged as a first-class control-plane action rather than an untracked side effect.

The CSV now includes:

- request-level dry-run metadata
- document identity and mime
- effective preview status
- effective preview failure reason
- effective preview failure category
- effective preview last updated
- predicted state/outcome/reason

### Frontend

Extended [opsRecoveryService.ts](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/services/opsRecoveryService.ts) with `exportDryRunCsv(...)`, using a POST blob download.

Updated [PreviewDiagnosticsPage.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx):

- added `dryRunExporting` state
- added `handleExportGlobalDryRun()`
- added `Export Dry-run CSV` beside `Run Dry-run`

This keeps the export entry exactly where operators build and inspect the plan, instead of forcing them into a separate async export area.

## Files

### Backend

- `ecm-core/src/main/java/com/ecm/core/controller/OpsRecoveryController.java`
- `ecm-core/src/test/java/com/ecm/core/controller/OpsRecoveryControllerSecurityTest.java`

### Frontend

- `ecm-frontend/src/services/opsRecoveryService.ts`
- `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`

## Outcome

Athena’s ops recovery planner now supports:

- richer rendition-backed dry-run samples
- immediate in-page inspection
- direct CSV export of the exact same plan

That is a more complete operator loop than a UI-only dry-run and continues pushing Athena beyond the baseline reference implementation on recovery diagnostics depth and workflow completeness.
