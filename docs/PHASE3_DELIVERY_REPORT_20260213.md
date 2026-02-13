# Phase 3 Delivery Report (OCR + Preview Queue Hardening) — 2026-02-13

This report consolidates the Phase 3 plan, implementation, and verification into a single, reviewable document.

## Scope

Phase 3 goal: deliver a production-lean, observable OCR ingestion loop and harden preview generation flows so that:

- Uploads remain fast (async post-processing).
- Jobs are observable (status + reason), retryable, and safe by default.
- Search and Advanced Search reflect correct preview-status behavior.
- CI gates are stable and representative of production constraints.

Primary PR:

- `https://github.com/zensgit/Athena/pull/15`

## Delivered (What Changed)

### 1) Redis-backed delayed queue backend (Preview + OCR)

Added a restart-safe delayed queue store (Redis ZSET schedule + HASH attempt tracking) and wired it into both queues.

- Store: `ecm-core/src/main/java/com/ecm/core/queue/RedisScheduledQueueStore.java`
- OCR queue: `ecm-core/src/main/java/com/ecm/core/ocr/OcrQueueService.java`
- Preview queue: `ecm-core/src/main/java/com/ecm/core/preview/PreviewQueueService.java`

Runtime toggles (safe defaults):

- `ECM_OCR_QUEUE_BACKEND=memory|redis` (default: `memory`)
- `ECM_PREVIEW_QUEUE_BACKEND=memory|redis` (default: `memory`)

Docker composition wires the env vars in `docker-compose.yml` but still defaults both to `memory` unless explicitly set.

### 2) Search / Advanced Search preview-status stability

Hardened Advanced Search preview-status filter + action rendering to avoid stale/misaligned retry actions under filtered slices.

- UI: `ecm-frontend/src/pages/AdvancedSearchPage.tsx`
- E2E: `ecm-frontend/e2e/search-preview-status.spec.ts`

### 3) Docker/CI cache safety with Redis present

Redis is used for queues, but Spring Cache should remain in-memory in Docker/CI to avoid serializer issues on complex payloads.

- Docker profile: `ecm-core/src/main/resources/application-docker.yml`
  - `spring.cache.type: simple`

### 4) CI E2E gate reliability (stack readiness)

Two CI stability fixes were required to make the `Frontend E2E Core Gate` deterministic:

1. **E2E stack minimalism**: removed unnecessary services (Collabora + GreenMail + ML service) from the gate stack to reduce runner resource contention.
2. **Frontend startup when Collabora is absent**: Nginx previously failed hard if `collabora` DNS did not exist; fixed by using an upstream variable for Collabora proxying.

Files:

- Workflow: `.github/workflows/ci.yml`
- Frontend nginx: `ecm-frontend/nginx.conf`

## Public Interfaces / Config Changes

No breaking public REST API changes were introduced.

Additive configuration:

- `ECM_OCR_QUEUE_BACKEND` / `ECM_PREVIEW_QUEUE_BACKEND` (queue backend selection).

Behavioral guardrail (Docker profile only):

- `spring.cache.type=simple` to prevent Redis cache autoconfiguration in CI/Docker.

## Verification (How We Proved It Works)

### Local verification

Backend:

```bash
cd ecm-core
mvn -q test
```

Frontend E2E (core Phase 3 gate subset):

```bash
cd ecm-frontend
npx playwright test \
  e2e/ocr-queue-ui.spec.ts \
  e2e/pdf-preview.spec.ts \
  e2e/search-preview-status.spec.ts \
  --project=chromium
```

Weekly regression subset (Day 7 gate):

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

### CI verification

CI checks for PR #15 are passing (including the `Frontend E2E Core Gate`).

The E2E gate failures were traced to:

- Ecm-core readiness instability on CI runners (resource contention).
- Nginx failing to start when `collabora` is not running, even though WOPI is disabled for the E2E gate.

Both were fixed via the changes described in "CI E2E gate reliability".

## References (Design + Daily Verification)

Plan:

- `docs/NEXT_7DAY_PLAN_PHASE3_20260212.md`

Progress and rollups:

- `docs/PHASE3_EXECUTION_PROGRESS_20260213.md`
- `docs/RELEASE_NOTES_20260213_PHASE3.md`
- `docs/PHASE3_COMMIT_SPLIT_PLAN_20260213.md`

Daily design/verification:

- `docs/PHASE3_D1_OCR_ML_SERVICE_DESIGN_20260212.md`
- `docs/PHASE3_D1_OCR_ML_SERVICE_VERIFICATION_20260212.md`
- `docs/PHASE3_D2_OCR_E2E_SMOKE_DESIGN_20260212.md`
- `docs/PHASE3_D2_OCR_E2E_SMOKE_VERIFICATION_20260212.md`
- `docs/PHASE3_D3_OCR_UI_STATUS_DESIGN_20260212.md`
- `docs/PHASE3_D3_OCR_UI_STATUS_VERIFICATION_20260212.md`
- `docs/PHASE3_D4_OCR_ENRICHMENT_CORRESPONDENT_DESIGN_20260212.md`
- `docs/PHASE3_D4_OCR_ENRICHMENT_CORRESPONDENT_VERIFICATION_20260212.md`
- `docs/PHASE3_D5_REDIS_QUEUE_BACKEND_DESIGN_20260212.md`
- `docs/PHASE3_D5_REDIS_QUEUE_BACKEND_VERIFICATION_20260212.md`
- `docs/PHASE3_D6_RULE_DRYRUN_BACKFILL_GUARDRAILS_DESIGN_20260213.md`
- `docs/PHASE3_D6_RULE_DRYRUN_BACKFILL_GUARDRAILS_VERIFICATION_20260213.md`
- `docs/PHASE3_D7_FULL_REGRESSION_GATE_DESIGN_20260213.md`
- `docs/PHASE3_D7_FULL_REGRESSION_GATE_VERIFICATION_20260213.md`

