# Phase 288 - Odoo Async Retry Dry-run Selected Retry Governance (Verification)

## Date
- 2026-03-13

## Verification Scope
- Frontend:
  - rendition dry-run candidate panel selected-retry workflow.
  - ops dry-run candidate panel selected-retry workflow.
  - selection lifecycle reset and disabled-state behavior.
- Backend contract reuse checks:
  - selected retry uses existing `retry-terminal/by-task-ids` endpoints.
  - dry-run to selected-retry transition remains consistent with terminal/dedup semantics.

## Commands
- Frontend lint:
  - `cd ecm-frontend && npm run -s lint -- --max-warnings=0`
- Frontend build:
  - `cd ecm-frontend && npm run -s build`
- Backend compile:
  - `cd ecm-core && mvn -q -DskipTests compile`
- Backend targeted tests:
  - `cd ecm-core && mvn -q -Dtest=PreviewDiagnosticsControllerSecurityTest#diagnosticsRenditionResourcesAsyncRetryTerminalBulkForAdmin,OpsRecoveryControllerSecurityTest#historyExportAsyncRetryTerminalBulkForAdmin test`
- Mocked e2e (panel behavior):
  - `cd ecm-frontend && npm start` (serve UI at `http://localhost:3000`)
  - `cd ecm-frontend && ECM_UI_URL=http://localhost:3000 npx playwright test e2e/admin-preview-diagnostics.mock.spec.ts`

## Results
- Frontend lint: `passed`
- Frontend build: `passed`
- Backend compile: `passed`
- Backend targeted tests: `passed`
- Mocked e2e: `passed`

## Manual Checks
- Dry-run result table only allows retryable rows to be selected: `pending` (manual run not executed in this turn)
- `Retry Selected` sends deduplicated `sourceTaskIds` payload: `pending` (manual run not executed in this turn)
- After selected retry:
  - task-center list refreshes,
  - summary chips update,
  - dry-run selection snapshot is cleared: `pending` (manual run not executed in this turn)

## Conclusion
- Verification status: `automated checks passed`
- Final pass/fail decision: `pass (automation), manual spot-check pending`
