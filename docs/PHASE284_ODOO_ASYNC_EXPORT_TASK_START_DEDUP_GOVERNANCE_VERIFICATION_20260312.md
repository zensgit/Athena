# Phase 284 - Odoo Async Export Start Dedup Governance (Verification)

## Date
- 2026-03-12

## Verification Scope
- Backend:
  - async export start dedup for rendition resources center.
  - async export start dedup for ops recovery history center.
  - `202 + Location` contract verification for start/retry endpoints in both centers.
  - retry regression for both centers remains valid.
- Frontend:
  - start dedup toast integration lint/build safety.

## Commands
- Backend:
  - `cd ecm-core && mvn -Dtest=PreviewDiagnosticsControllerSecurityTest#diagnosticsRenditionResourcesAsyncExportTaskCenterForAdmin+diagnosticsRenditionResourcesAsyncStartDeduplicatesToActiveTask+diagnosticsRenditionResourcesAsyncRetryForAdmin+diagnosticsRenditionResourcesAsyncRetryDeduplicatesToActiveTask,OpsRecoveryControllerSecurityTest#historyExportAsyncTaskEndpointsForAdmin+historyExportAsyncSummaryAndCleanupForAdmin+historyExportAsyncStartDeduplicatesToActiveTask+historyExportAsyncRetryForAdmin+historyExportAsyncRetryDeduplicatesToActiveTask+historyExportAsyncRetryRejectsNonTerminalTask test`
- Frontend:
  - `cd ecm-frontend && npm run lint -- --max-warnings=0`
  - `cd ecm-frontend && npm run build`

## Results
- Backend targeted tests: Passed (`Tests run: 10, Failures: 0, Errors: 0`).
- Frontend lint: Passed.
- Frontend build: Passed.

## Conclusion
- Phase284 validates start-time dedup governance and `202 + Location` async contract alignment for both additional async export centers, with no observed regression in terminal retry flow or frontend integration.
