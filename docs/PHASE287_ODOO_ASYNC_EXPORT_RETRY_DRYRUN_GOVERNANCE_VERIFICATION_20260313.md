# Phase 287 - Odoo Async Export Retry Dry-run Governance (Verification)

## Date
- 2026-03-13

## Verification Scope
- Frontend:
  - rendition async task center dry-run + dry-run CSV wiring.
  - ops recovery async task center dry-run + dry-run CSV wiring.
  - mocked admin diagnostics e2e coverage for rendition + ops dry-run/selected-retry flows.
- Backend:
  - new dry-run and dry-run-export endpoints for rendition and ops async retry-terminal flows.
  - updated security tests for both flows.

## Commands
- Frontend lint:
  - `cd ecm-frontend && npm run -s lint -- --max-warnings=0`
- Frontend build:
  - `cd ecm-frontend && npm run -s build`
- Frontend mocked e2e lint:
  - `cd ecm-frontend && npx eslint e2e/admin-preview-diagnostics.mock.spec.ts`
- Frontend mocked e2e runtime:
  - `cd ecm-frontend && npx serve -s build -l 5500`
  - `cd ecm-frontend && npm run -s e2e -- e2e/admin-preview-diagnostics.mock.spec.ts --project=chromium`
- Backend compile:
  - `cd ecm-core && mvn -q -DskipTests compile`
- Backend targeted security tests:
  - `cd ecm-core && mvn -q -Dtest=PreviewDiagnosticsControllerSecurityTest#diagnosticsRenditionResourcesAsyncRetryTerminalBulkForAdmin,OpsRecoveryControllerSecurityTest#historyExportAsyncRetryTerminalBulkForAdmin test`

## Results
- Frontend lint: Passed.
- Frontend build: Passed.
- Frontend mocked e2e lint: Passed.
- Frontend mocked e2e runtime: Passed.
- Backend compile: Passed.
- Backend targeted security tests: Passed.

## Endpoint Contract Checks Covered by Tests
- Preview rendition dry-run:
  - `POST /api/v1/preview/diagnostics/renditions/resources/export-async/retry-terminal/dry-run`
  - `GET /api/v1/preview/diagnostics/renditions/resources/export-async/retry-terminal/dry-run/export`
- Ops recovery dry-run:
  - `POST /api/v1/ops/recovery/history/export-async/retry-terminal/dry-run`
  - `GET /api/v1/ops/recovery/history/export-async/retry-terminal/dry-run/export`

## Conclusion
- Phase287 increment is complete for this slice:
  - backend retry-terminal dry-run governance is available for rendition+ops async centers,
  - frontend operator task-center UI is fully wired,
  - mocked admin diagnostics flow now covers dry-run, dry-run CSV export, and selected retry for rendition + ops async centers,
  - verification confirms compile, integration, mock e2e, and endpoint contract behavior.
