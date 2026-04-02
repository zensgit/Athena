# Phase167 Verification: Preview Dead-Letter Redis Persistence Hardening

## Date
2026-03-06

## Scope
- dead-letter Redis backend behavior (record/list/remove).
- diagnostics payload contract for backend metadata.
- frontend dead-letter metadata rendering.
- Day7 gate regression.

## Commands and results

1) Backend targeted tests
- Command:
  - `cd ecm-core && mvn -q -Dtest=PreviewDiagnosticsControllerSecurityTest,PreviewQueueServiceTest,PreviewQueueServiceRedisBackendTest,PreviewDeadLetterRegistryTest,PreviewDeadLetterRegistryRedisBackendTest test`
- Result:
  - PASS
- Note:
  - Testcontainers may log Docker-environment warnings; Redis container tests are assumption-guarded and suite remains PASS.

2) Backend compile
- Command:
  - `cd ecm-core && mvn -q -DskipTests compile`
- Result:
  - PASS

3) Frontend dead-letter UI checks
- Commands:
  - `cd ecm-frontend && npm run -s lint -- src/pages/PreviewDiagnosticsPage.tsx src/services/previewDiagnosticsService.ts`
  - `cd ecm-frontend && npx playwright test e2e/admin-preview-diagnostics.mock.spec.ts --project=chromium`
- Result:
  - PASS

4) Day7 delivery gate
- Command:
  - `scripts/phase164-preview-day7-delivery-gate.sh`
- Result:
  - PASS

## Verified outcomes
- dead-letter entries can persist in Redis with bounded capacity and TTL.
- diagnostics API now exposes dead-letter backend mode and retention TTL.
- admin dead-letter panel shows backend/TTL metadata without regressing replay actions.
- full delivery gate (backend + frontend + mocked e2e) remains green.
