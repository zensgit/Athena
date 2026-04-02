# Phase168 Verification: Preview Dead-Letter Auto-Replay, Export, and Contention Hardening

## Date
2026-03-06

## Scope
- auto replay policy execution and cooldown/category gating.
- dead-letter CSV export contract and audit hook coverage.
- Redis enqueue contention/idempotency stress check.
- frontend export action regression.

## Commands and results

1) Backend targeted tests
- Command:
  - `cd ecm-core && mvn -q -Dtest=PreviewDiagnosticsControllerSecurityTest,PreviewQueueServiceTest,PreviewQueueServiceRedisBackendTest,PreviewDeadLetterRegistryTest,PreviewDeadLetterRegistryRedisBackendTest test`
- Result:
  - PASS

2) Backend compile
- Command:
  - `cd ecm-core && mvn -q -DskipTests compile`
- Result:
  - PASS

3) Frontend lint/build
- Commands:
  - `cd ecm-frontend && npm run -s lint -- src/pages/PreviewDiagnosticsPage.tsx src/services/previewDiagnosticsService.ts`
  - `cd ecm-frontend && npm run -s build`
- Result:
  - PASS

4) Mocked Playwright
- Command:
  - `cd ecm-frontend && python3 -m http.server 5500 --directory build`
  - `cd ecm-frontend && ECM_UI_URL=http://localhost:5500 npx playwright test e2e/admin-preview-diagnostics.mock.spec.ts --project=chromium`
- Result:
  - PASS

5) Day7 gate script
- Command:
  - `scripts/phase164-preview-day7-delivery-gate.sh`
- Result:
  - PASS

## Verified outcomes
- dead-letter auto replay now supports category allowlist + cooldown + capped replay batch.
- dead-letter export API returns CSV with replay metadata and consistent response headers.
- replay/export operations emit audit events.
- Redis queue remains idempotent under concurrent enqueue contention for same document id.
- diagnostics UI export action stays stable with existing dead-letter replay flow.
