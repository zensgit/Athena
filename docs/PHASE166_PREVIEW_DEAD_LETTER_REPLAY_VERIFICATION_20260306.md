# Phase166 Verification: Preview Dead-Letter Replay Loop

## Date
2026-03-06

## Scope
- dead-letter registry behavior.
- queue integration for record/remove semantics.
- diagnostics API contract + admin access control.
- frontend dead-letter panel and mocked replay flow.

## Commands and results

1) Backend targeted tests
- Command:
  - `cd ecm-core && mvn -q -Dtest=PreviewDiagnosticsControllerSecurityTest,PreviewQueueServiceTest,PreviewQueueServiceRedisBackendTest,PreviewDeadLetterRegistryTest test`
- Result:
  - PASS
- Note:
  - Testcontainers warns about missing local Docker runtime; redis test assumes/guards this and suite remains PASS.

2) Frontend lint
- Command:
  - `cd ecm-frontend && npm run lint`
- Result:
  - PASS

3) Frontend build
- Command:
  - `cd ecm-frontend && npm run build`
- Result:
  - PASS

4) Mocked Playwright
- Command:
  - `cd ecm-frontend && python3 -m http.server 5500 --directory build`
  - `cd ecm-frontend && npx playwright test e2e/admin-preview-diagnostics.mock.spec.ts --project=chromium`
- Result:
  - PASS

5) Day7 gate script
- Command:
  - `scripts/phase164-preview-day7-delivery-gate.sh`
- Result:
  - PASS

## Verified behaviors
- terminal preview failures are captured in dead-letter registry with policy and stage metadata.
- replay-batch API queues selected dead-letter entries and clears queued entries from registry.
- admin page shows dead-letter panel with filter/select/replay actions.
- mocked E2E confirms dead-letter replay request is emitted and UI flow remains stable.
