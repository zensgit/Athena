# Phase161 Verification: Preview Failure Policy Profiles

## Date
2026-03-06

## Scope
- Backend policy registry + queue policy execution.
- Admin diagnostics policy API.
- Frontend policy profile panel and mocked E2E wiring.

## Commands and results

1) Backend targeted tests
- Command:
  - `cd ecm-core && mvn -q -Dtest=PreviewFailurePolicyRegistryTest,PreviewTransformTraceBufferTest,PreviewDiagnosticsControllerSecurityTest,PreviewQueueServiceTest test`
- Result:
  - PASS

2) Backend compile sanity
- Command:
  - `cd ecm-core && mvn -q -DskipTests compile`
- Result:
  - PASS

3) Frontend lint (changed sources)
- Command:
  - `cd ecm-frontend && npm run -s lint -- src/pages/PreviewDiagnosticsPage.tsx src/services/previewDiagnosticsService.ts`
- Result:
  - PASS

4) Frontend production build
- Command:
  - `cd ecm-frontend && npm run -s build`
- Result:
  - PASS

5) Mocked E2E
- Command:
  - `cd ecm-frontend && npx playwright test e2e/admin-preview-diagnostics.mock.spec.ts --project=chromium`
- Result:
  - PASS

## Verified behaviors
- Policy list/update APIs are admin protected and return structured payload.
- Queue service uses policy profile for retry scheduling and quiet-period gate.
- Policy bounds are clamped to safe ranges.
- Diagnostics page renders policy panel with inline editing + save action.
