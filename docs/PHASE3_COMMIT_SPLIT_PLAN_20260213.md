# Phase 3 Commit Split Plan (2026-02-13)

This document proposes a clean, review-friendly commit split for the current working tree.

## Branch

- `feat/phase3-ocr-queue-20260212`

## Commit Strategy

1. Keep backend queue backend changes isolated from frontend behavior changes.
2. Keep test-only signature/contract fixes separate from feature commits.
3. Keep docs as final commit to preserve traceability.
4. Keep local/runtime config changes (`.env`) optional.

## Proposed Commit Order

### Commit 1: `feat(core): add redis backend for preview and OCR queues`

Files:

- `ecm-core/pom.xml`
- `ecm-core/src/main/java/com/ecm/core/queue/RedisScheduledQueueStore.java`
- `ecm-core/src/main/java/com/ecm/core/ocr/OcrQueueService.java`
- `ecm-core/src/main/java/com/ecm/core/preview/PreviewQueueService.java`
- `ecm-core/src/test/java/com/ecm/core/queue/RedisScheduledQueueStoreTest.java`
- `ecm-core/src/test/java/com/ecm/core/ocr/OcrQueueServiceRedisBackendTest.java`
- `ecm-core/src/test/java/com/ecm/core/preview/PreviewQueueServiceRedisBackendTest.java`
- `ecm-core/src/test/java/com/ecm/core/ocr/OcrQueueServiceTest.java`
- `ecm-core/src/test/java/com/ecm/core/preview/PreviewQueueServiceTest.java`

Commands:

```bash
git add \
  ecm-core/pom.xml \
  ecm-core/src/main/java/com/ecm/core/queue/RedisScheduledQueueStore.java \
  ecm-core/src/main/java/com/ecm/core/ocr/OcrQueueService.java \
  ecm-core/src/main/java/com/ecm/core/preview/PreviewQueueService.java \
  ecm-core/src/test/java/com/ecm/core/queue/RedisScheduledQueueStoreTest.java \
  ecm-core/src/test/java/com/ecm/core/ocr/OcrQueueServiceRedisBackendTest.java \
  ecm-core/src/test/java/com/ecm/core/preview/PreviewQueueServiceRedisBackendTest.java \
  ecm-core/src/test/java/com/ecm/core/ocr/OcrQueueServiceTest.java \
  ecm-core/src/test/java/com/ecm/core/preview/PreviewQueueServiceTest.java
git commit -m "feat(core): add redis backend for preview and OCR queues"
```

### Commit 2: `test(core): align controller and mail diagnostics tests with current signatures`

Files:

- `ecm-core/src/test/java/com/ecm/core/controller/AnalyticsControllerTest.java`
- `ecm-core/src/test/java/com/ecm/core/controller/SearchControllerSecurityTest.java`
- `ecm-core/src/test/java/com/ecm/core/controller/SearchControllerTest.java`
- `ecm-core/src/test/java/com/ecm/core/integration/mail/controller/MailAutomationControllerDiagnosticsTest.java`
- `ecm-core/src/test/java/com/ecm/core/integration/mail/controller/MailAutomationControllerSecurityTest.java`

Commands:

```bash
git add \
  ecm-core/src/test/java/com/ecm/core/controller/AnalyticsControllerTest.java \
  ecm-core/src/test/java/com/ecm/core/controller/SearchControllerSecurityTest.java \
  ecm-core/src/test/java/com/ecm/core/controller/SearchControllerTest.java \
  ecm-core/src/test/java/com/ecm/core/integration/mail/controller/MailAutomationControllerDiagnosticsTest.java \
  ecm-core/src/test/java/com/ecm/core/integration/mail/controller/MailAutomationControllerSecurityTest.java
git commit -m "test(core): align controller and diagnostics tests to current contracts"
```

### Commit 3: `fix(frontend): stabilize advanced search preview-status issue panel and e2e`

Files:

- `ecm-frontend/src/pages/AdvancedSearchPage.tsx`
- `ecm-frontend/e2e/search-preview-status.spec.ts`

Commands:

```bash
git add \
  ecm-frontend/src/pages/AdvancedSearchPage.tsx \
  ecm-frontend/e2e/search-preview-status.spec.ts
git commit -m "fix(frontend): stabilize advanced search preview-status actions and e2e"
```

### Commit 4: `chore(config): expose queue backend toggles for docker runtime`

Files:

- `docker-compose.yml`
- `.env` (optional)

Commands:

```bash
git add docker-compose.yml
# Optional only if team accepts tracked .env defaults:
# git add .env
git commit -m "chore(config): add queue backend toggles for preview and OCR"
```

### Commit 5: `docs(phase3): add day5-day7 design/verification and execution progress`

Files:

- `docs/NEXT_7DAY_PLAN_PHASE3_20260212.md`
- `docs/DOCS_INDEX_20260212.md`
- `docs/PHASE3_D5_REDIS_QUEUE_BACKEND_DESIGN_20260212.md`
- `docs/PHASE3_D5_REDIS_QUEUE_BACKEND_VERIFICATION_20260212.md`
- `docs/PHASE3_D6_RULE_DRYRUN_BACKFILL_GUARDRAILS_DESIGN_20260213.md`
- `docs/PHASE3_D6_RULE_DRYRUN_BACKFILL_GUARDRAILS_VERIFICATION_20260213.md`
- `docs/PHASE3_D7_FULL_REGRESSION_GATE_DESIGN_20260213.md`
- `docs/PHASE3_D7_FULL_REGRESSION_GATE_VERIFICATION_20260213.md`
- `docs/PHASE3_EXECUTION_PROGRESS_20260213.md`
- `docs/PHASE3_COMMIT_SPLIT_PLAN_20260213.md`
- `docs/RELEASE_NOTES_20260213_PHASE3.md`

Commands:

```bash
git add \
  docs/NEXT_7DAY_PLAN_PHASE3_20260212.md \
  docs/DOCS_INDEX_20260212.md \
  docs/PHASE3_D5_REDIS_QUEUE_BACKEND_DESIGN_20260212.md \
  docs/PHASE3_D5_REDIS_QUEUE_BACKEND_VERIFICATION_20260212.md \
  docs/PHASE3_D6_RULE_DRYRUN_BACKFILL_GUARDRAILS_DESIGN_20260213.md \
  docs/PHASE3_D6_RULE_DRYRUN_BACKFILL_GUARDRAILS_VERIFICATION_20260213.md \
  docs/PHASE3_D7_FULL_REGRESSION_GATE_DESIGN_20260213.md \
  docs/PHASE3_D7_FULL_REGRESSION_GATE_VERIFICATION_20260213.md \
  docs/PHASE3_EXECUTION_PROGRESS_20260213.md \
  docs/PHASE3_COMMIT_SPLIT_PLAN_20260213.md \
  docs/RELEASE_NOTES_20260213_PHASE3.md
git commit -m "docs(phase3): add execution progress, validation artifacts, and release notes"
```

## Verification Checklist Before Push

```bash
cd ecm-core && mvn -q test
cd ../ecm-frontend && npx playwright test e2e/ocr-queue-ui.spec.ts e2e/pdf-preview.spec.ts e2e/search-preview-status.spec.ts --project=chromium
```

