# Next 7-Day Delivery Plan (Phase 3: OCR + Automation Hardening) — 2026-02-12

This Phase 3 plan is grounded in:
- `docs/IMPROVEMENT_PLAN.md`
- `docs/ALFRESCO_GAP_ANALYSIS_20260129.md`
- `reference-projects/paperless-ngx` (mail intake + OCR pipeline patterns, inspiration only; GPLv3)
- `reference-projects/alfresco-community-repo` (renditions/aspects/audit patterns, inspiration only; GPLv3)

## Goal

Deliver a **production-lean OCR ingestion loop** and a **hardened automation loop**:
- OCR fills the text gap for scanned PDFs/images so search, auto-matching, and ML suggestions work reliably.
- Jobs are observable (status/reason), retryable, and do not block uploads.

## Non-Goals (This Phase)
- Full enterprise-grade job orchestration (DLQ, distributed workers, exactly-once delivery).
- Full Alfresco data model parity (custom models, multi-tenant search, multiple query languages).
- Copying any GPL reference code (ideas only).

## Guiding Principles
- Every day delivers: **feature + automated verification + design/verification MD**.
- Keep changes additive; do not break existing APIs without explicitly documenting.
- No secrets in git. OAuth tokens and credentials remain in `.env*` files (gitignored) and/or env vars.

## P0/P1 Backlog (Prioritized)

P0
- OCR extraction via `ml-service` + Core queue integration (fast upload, async OCR).
- OCR observability: metadata status + failure reason + last updated; manual queue endpoint.
- Search reindex after OCR completes.

P1
- UI surfacing: show OCR status + failure reason, and “Retry OCR / Force OCR” actions.
- Automation quality: tie OCR completion into correspondent auto-matching and optional ML suggestions (tags/category).
- Persist queue state (Redis-backed) to survive core restarts (preview + OCR).

P2
- Rule UX: templates + dry-run tooling for rules beyond upload pipeline (batch/backfill).
- OCR language selection UI + per-folder defaults.

## 7-Day Outline

### Day 1 (P0) — OCR API in `ml-service` + Core OCR Queue Skeleton
Deliverables:
- `ml-service`: `POST /api/ml/ocr` (Tesseract-backed OCR for PDF/images with conservative limits).
- `ecm-core`: `OcrQueueService` (in-memory queue with retries) + `POST /api/v1/documents/{id}/ocr/queue`.
- Event hook: enqueue OCR on version creation (guarded by feature flags inside service).
- Unit test: `OcrQueueServiceTest`.

Docs:
- `docs/PHASE3_D1_OCR_ML_SERVICE_DESIGN_20260212.md`
- `docs/PHASE3_D1_OCR_ML_SERVICE_VERIFICATION_20260212.md`

### Day 2 (P0) — OCR End-to-End: Update Index + Manual Validation Script
Deliverables:
- Ensure OCR completion updates ES index consistently (`SearchIndexService.updateDocument`).
- Add a small CLI smoke script that:
  - queues OCR for a chosen document id
  - polls node metadata until status becomes READY/FAILED
  - runs a search query expected to match OCR text

Verification:
- Targeted backend test(s) for index update invocation + idempotency.
- Optional Playwright (if we add UI for OCR status by Day 3).

### Day 3 (P1) — UI Surfacing: OCR Status + Retry Actions
Deliverables:
- Search/Advanced Search/Details:
  - show OCR chip: `PROCESSING/READY/FAILED/SKIPPED` based on metadata
  - show failure reason (tooltip)
  - actions: `Retry OCR`, `Force OCR` (calls `/documents/{id}/ocr/queue`)
- Playwright E2E:
  - queue OCR from UI and assert toast + status chip transition (mockable / time-bounded).

### Day 4 (P1) — OCR-Driven Enrichment Hooks
Deliverables:
- On OCR success:
  - attempt correspondent auto-match if absent
  - optionally run ML suggestion hooks (tags/category) behind flags
- Backend tests for “does not overwrite user-set values”.

### Day 5 (P1) — Redis-Backed Queue (Preview + OCR)
Deliverables:
- Move in-memory job queues to Redis lists/sets:
  - ensures jobs survive restarts
  - de-dup by documentId
- Backward-compatible public API unchanged.

### Day 6 (P2) — Rules UX Improvements (Dry Run + Backfill Guardrails)
Deliverables:
- Rule dry-run endpoint returns match summary without applying actions.
- Backfill limits and safe defaults documented.

### Day 7 (Ops) — Full Regression Gate + Docs Index Update
Deliverables:
- Run:
  - backend targeted tests
  - Playwright suite (or regression subset)
  - docker build smoke
- Update docs index with Phase 3 links.

