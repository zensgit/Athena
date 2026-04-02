# Phase 283 - Odoo Async Export Terminal Retry Governance (Verification)

## Date
- 2026-03-12

## Verification Scope
- Backend:
  - retry authorization guard (`admin only`) for two async centers.
  - terminal retry success path and non-terminal retry rejection.
- Frontend:
  - Retry API contract typing + page integration lint/build safety.

## Commands
- Backend:
  - `cd ecm-core && mvn -Dtest=PreviewDiagnosticsControllerSecurityTest#diagnosticsRequiresAdmin+diagnosticsRenditionResourcesAsyncRetryForAdmin,OpsRecoveryControllerSecurityTest#requiresAdminRole+historyExportAsyncRetryForAdmin+historyExportAsyncRetryRejectsNonTerminalTask test`
- Frontend:
  - `cd ecm-frontend && npm run lint -- --max-warnings=0`
  - `cd ecm-frontend && npm run build`

## Results
- Backend targeted tests: Passed (`Tests run: 5, Failures: 0, Errors: 0`).
- Frontend lint: Passed.
- Frontend build: Passed.

## Conclusion
- Phase283 terminal retry governance is validated for rendition resources async export and ops recovery history async export, with backend guardrails and frontend task-center integration in place.
