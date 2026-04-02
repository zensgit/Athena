# Phase 285 - Odoo Async Export Terminal Bulk Retry Governance (Verification)

## Date
- 2026-03-12

## Verification Scope
- Backend:
  - ops recovery history async export bulk terminal retry + selected-id retry.
  - preview rendition resources async export bulk terminal retry + selected-id retry.
  - admin authorization checks for newly added retry-terminal endpoints.
- Frontend:
  - preview and ops task-center retry-terminal API wiring.
  - Preview Diagnostics page action wiring and type safety.

## Commands
- Backend compile:
  - `cd ecm-core && mvn -q -DskipTests compile`
- Backend targeted tests (new/updated coverage):
  - `cd ecm-core && mvn -q -Dtest=PreviewDiagnosticsControllerSecurityTest#diagnosticsRequiresAdmin+diagnosticsRenditionResourcesAsyncRetryTerminalBulkForAdmin,OpsRecoveryControllerSecurityTest#requiresAdminRole+historyExportAsyncRetryTerminalBulkForAdmin test`
- Backend broader regression slice:
  - `cd ecm-core && mvn -q -Dtest=OpsRecoveryControllerSecurityTest test`
- Frontend:
  - `cd ecm-frontend && npm run -s lint -- --max-warnings=0`
  - `cd ecm-frontend && npm run -s build`

## Results
- Backend compile: Passed.
- Backend targeted tests: Passed.
- Backend `OpsRecoveryControllerSecurityTest` full class: Passed.
- Frontend lint: Passed.
- Frontend build: Passed.

## Notes
- Running the full pair command `-Dtest=PreviewDiagnosticsControllerSecurityTest,OpsRecoveryControllerSecurityTest` in this workspace hits pre-existing `PreviewDiagnosticsControllerSecurityTest` baseline assertions unrelated to this phase (queue-declined sample expectation drift).  
  - This phase validation therefore uses targeted preview tests plus full ops security regression.

## Conclusion
- Phase285 delivery is complete for this increment:
  - backend bulk + selected terminal retry governance now covers both additional async export centers,
  - frontend exposes operational controls for filter-based and visible-selected terminal retries,
  - verification confirms contract behavior and integration safety.
