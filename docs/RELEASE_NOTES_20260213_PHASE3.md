# Release Notes — Phase 3 (2026-02-13)

This release closes the planned Phase 3 OCR/preview queue hardening scope with backend queue resiliency, search preview-status stability, and completed regression gates.

## Highlights

## 1) Redis-backed queue backend for OCR and preview jobs

- Added Redis-backed delayed queue store:
  - `ecm-core/src/main/java/com/ecm/core/queue/RedisScheduledQueueStore.java`
- Integrated queue backend selection into:
  - `ecm-core/src/main/java/com/ecm/core/ocr/OcrQueueService.java`
  - `ecm-core/src/main/java/com/ecm/core/preview/PreviewQueueService.java`
- Added dependency:
  - `ecm-core/pom.xml` -> `spring-boot-starter-data-redis`

Runtime toggles:

- `ECM_OCR_QUEUE_BACKEND` (`memory` or `redis`)
- `ECM_PREVIEW_QUEUE_BACKEND` (`memory` or `redis`)

Docker defaults added in:

- `docker-compose.yml`

## 2) Search / Advanced Search preview-status behavior hardening

- Improved result-level preview issue summary scoping against active preview-status filters.
- Avoids stale/misaligned retry action rendering for unsupported-only result slices.

Files:

- `ecm-frontend/src/pages/AdvancedSearchPage.tsx`
- `ecm-frontend/e2e/search-preview-status.spec.ts`

## 3) Controller test alignment and diagnostics regression safety

Updated backend tests to current service method signatures and diagnostics expectations:

- `ecm-core/src/test/java/com/ecm/core/controller/SearchControllerTest.java`
- `ecm-core/src/test/java/com/ecm/core/controller/SearchControllerSecurityTest.java`
- `ecm-core/src/test/java/com/ecm/core/controller/AnalyticsControllerTest.java`
- `ecm-core/src/test/java/com/ecm/core/integration/mail/controller/MailAutomationControllerDiagnosticsTest.java`
- `ecm-core/src/test/java/com/ecm/core/integration/mail/controller/MailAutomationControllerSecurityTest.java`

## Verification Summary

## Backend

```bash
cd ecm-core && mvn -q test
```

Result:

- Passed (exit code `0`)

Targeted Day 6 guardrail run:

```bash
cd ecm-core && mvn -q -Dtest=ScheduledRuleRunnerTest,RuleEngineServiceValidationTest,MailAutomationControllerDiagnosticsTest,MailAutomationControllerSecurityTest test
```

Result:

- Passed (exit code `0`)

## Frontend E2E

Core Phase 3 gate:

```bash
cd ecm-frontend && npx playwright test e2e/ocr-queue-ui.spec.ts e2e/pdf-preview.spec.ts e2e/search-preview-status.spec.ts --project=chromium
```

Result:

- `10 passed`

Day 7 weekly subset:

```bash
cd ecm-frontend
ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 ECM_E2E_WORKERS=1 \
  npx playwright test --workers=1 \
    e2e/ui-smoke.spec.ts \
    e2e/search-view.spec.ts \
    e2e/search-preview-status.spec.ts \
    e2e/pdf-preview.spec.ts \
    e2e/ocr-queue-ui.spec.ts \
    --project=chromium
```

Result:

- `22 passed`

## Documentation Added

- `docs/PHASE3_D5_REDIS_QUEUE_BACKEND_DESIGN_20260212.md`
- `docs/PHASE3_D5_REDIS_QUEUE_BACKEND_VERIFICATION_20260212.md`
- `docs/PHASE3_D6_RULE_DRYRUN_BACKFILL_GUARDRAILS_DESIGN_20260213.md`
- `docs/PHASE3_D6_RULE_DRYRUN_BACKFILL_GUARDRAILS_VERIFICATION_20260213.md`
- `docs/PHASE3_D7_FULL_REGRESSION_GATE_DESIGN_20260213.md`
- `docs/PHASE3_D7_FULL_REGRESSION_GATE_VERIFICATION_20260213.md`
- `docs/PHASE3_EXECUTION_PROGRESS_20260213.md`
- `docs/PHASE3_COMMIT_SPLIT_PLAN_20260213.md`

## Pull Request

- PR: `https://github.com/zensgit/Athena/pull/15`

## Known Notes

1. Local Docker/Testcontainers warnings can still appear on unstable desktop Docker environments; current verification gates pass.
2. Keep using the Day 7 regression subset as pre-merge minimum gate for this branch.
