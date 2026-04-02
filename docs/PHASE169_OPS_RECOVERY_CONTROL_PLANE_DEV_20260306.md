# Phase 169 - Ops Recovery Control Plane (Development)

## Date
2026-03-06

## Goal
- Deliver unified admin recovery control-plane endpoints under `/api/v1/ops/recovery`.
- Keep legacy preview diagnostics endpoints backward-compatible.
- Introduce shared recovery semantics (`JobState`, `FailureCategory`) for cross-domain UI/API evolution.

## Implemented

### 1) New backend controller
- Added `OpsRecoveryController`:
  - `POST /api/v1/ops/recovery/queue-by-reason`
  - `POST /api/v1/ops/recovery/queue-by-window`
  - `POST /api/v1/ops/recovery/replay-batch`
  - `POST /api/v1/ops/recovery/dry-run`
- Security:
  - Admin-only via `@PreAuthorize("hasRole('ADMIN')")`.

### 2) Unified recovery payload model
- Added response enums in controller payload:
  - `JobState`: `READY|PROCESSING|FAILED|QUEUED|PENDING|SKIPPED|UNSUPPORTED|UNKNOWN`
  - `FailureCategory`: `TEMPORARY|PERMANENT|UNSUPPORTED|UNKNOWN`
- Added structured batch and dry-run payloads:
  - candidate scan metadata (`totalCandidates`, `scanned`, `matched`, `truncated`)
  - queue outcome metadata (`requested`, `deduplicated`, `queued`, `skipped`, `failed`)
  - per-document item details.

### 3) Preview domain wiring
- Reused existing preview queue/dead-letter mechanics:
  - queue via `PreviewQueueService.enqueue(...)`
  - replay success cleanup via `PreviewDeadLetterRegistry.remove(...)`
- Added scan/filter handling for:
  - reason, category, retryable, days-window, maxDocuments, force.

### 4) Frontend service contract
- Added `ecm-frontend/src/services/opsRecoveryService.ts`:
  - typed request/response contracts for all 4 endpoints.
  - ready for Operations Center page integration.

## Compatibility notes
- Existing `/api/v1/preview/diagnostics/**` endpoints remain unchanged.
- New controller currently supports `domain=PREVIEW`; non-preview domains return `400`.
