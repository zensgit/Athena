# Next 7-Day Parallel Plan (Alfresco Surpass) - 2026-03-09

## Goal

Move Athena preview/retry subsystem from “batch retry features complete” to “governed, deduplicated, failure-ledgered, hash-safe pipeline”, aligned with and beyond Alfresco operational patterns.

## Day 1 (completed in this cycle)

1. Search-scope preview batch capabilities API.
2. Frontend worker-count tuning and payload propagation.
3. Mocked e2e + backend security/controller tests.

## Day 2-7 parallel workstreams

## Progress Update (2026-03-11)

1. Stream A delivered:
   - Phase253: failure-ledger persisted lifecycle + reset APIs/UI.
   - Phase254: failure-ledger reset-by-filter + CSV export + audit.
2. Stream B delivered increment:
   - Phase255: preview queue health diagnostics summary API/UI + tests.
   - Phase256: preview queue diagnostics CSV export + audit.
   - Phase257: queue diagnostics state/query filtering + metadata-enriched sample + filter-aligned CSV export.
   - Phase258: queue diagnostics cancel-active API/UI + audit + mocked e2e.
   - Phase259: queue declined diagnostics (category/query) + declined export/requeue/clear governance + mocked e2e.
   - Phase260: queue declined requeue dry-run API/UI + force strategy toggle + audit + mocked e2e.
   - Phase261: queue declined forceRequired filter (`ANY/YES/NO`) + category/force breakdown + filter-aligned export/requeue/dry-run/clear + tests.
   - Phase262: queue declined `windowHours` filter (`ANY/1..720`) + time-windowed declinedAt filtering + filter-aligned export/requeue/dry-run/clear + tests.
   - Phase263: queue declined async export task center (`start/list/summary/get/cancel/download/cleanup/cancel-active`) + UI task table/chips/actions + mocked e2e and controller security tests.
   - Phase264: queue declined async export status-filter governance (`ALL/QUEUED/RUNNING/COMPLETED/CANCELLED/FAILED`) + status-aware cleanup/cancel-active UI wiring + async lifecycle audit events + tests.
   - Phase265: queue declined async export terminal-task retry governance (`POST /export-async/{taskId}/retry`) + UI row-level retry action (`FAILED/CANCELLED`) + retry audit chain + mocked e2e and security tests.
   - Phase266: queue declined async export terminal bulk-retry governance (`POST /export-async/retry-terminal`) + UI bulk `Retry Terminal` action + `retried/skipped/failed` result telemetry + mocked e2e and security tests.
   - Phase267: queue declined async export terminal bulk-retry dry-run governance (`POST /export-async/retry-terminal/dry-run`) + UI bulk `Dry-run Terminal` action + `requested/retryable/skipped` precheck telemetry + mocked e2e and security tests.
   - Phase268: queue declined async export terminal selected-retry governance (`POST /export-async/retry-terminal/by-task-ids`) + UI dry-run candidate selection + bulk `Retry Selected` action + mocked e2e and security tests.
   - Phase269: queue declined async export terminal dry-run reason breakdown + CSV governance (`GET /export-async/retry-terminal/dry-run/export`) + UI `Export Dry-run CSV` + mocked e2e and security tests.
   - Phase270: queue declined async export start dedup governance (active task reuse by normalized filters) + API `deduplicated` response fields + UI reused toast + mocked e2e and security tests.
   - Phase271: queue declined requeue dry-run reason breakdown + CSV governance (`GET /queue/declined/requeue/dry-run/export`) + API `results[].reasonCode/reasonBreakdown[]` + UI `Export Requeue Dry-run CSV` + mocked e2e and security tests.
   - Phase272: queue declined requeue dry-run preflight reason governance + API `results[].preflight*` + `PREFLIGHT_<skipReason>` breakdown + dry-run CSV preflight columns + UI preflight diagnostics table column + mocked e2e and security tests.
   - Phase273: queue declined requeue dry-run async export task-center governance (`start/list/summary/get/cancel/download/cleanup/cancel-active`) + start dedup by normalized filter snapshot + UI requeue dry-run async task center + mocked e2e and security tests.
   - Phase274: queue declined requeue dry-run async export status-filter UI governance (`ALL/QUEUED/RUNNING/COMPLETED/CANCELLED/FAILED`) + filter-aligned list/summary/cleanup + active-only cancel-active guard + mocked e2e assertions.
   - Phase275: queue declined requeue dry-run async export terminal retry governance (`/{taskId}/retry`, `/retry-terminal`, `/retry-terminal/dry-run`, `/retry-terminal/dry-run/export`, `/retry-terminal/by-task-ids`) + UI row retry + dry-run candidate selection + retry selected/terminal + mocked e2e and security tests.
   - Phase276: queue declined requeue dry-run async export retry dedup governance (single/bulk/selected retry active-task reuse by normalized filter snapshot) + UI dedup reuse feedback + mocked e2e and security tests.
   - Phase277: queue declined async export retry dedup governance parity for non-requeue flow (`/{taskId}/retry`, `/retry-terminal`, `/retry-terminal/by-task-ids`) + `REUSED` outcome propagation + UI reused feedback + mocked e2e and security tests.
   - Phase278: queue declined async retry structured `reused` metrics governance (non-requeue + requeue dry-run async) + API response `reused` field + audit `reused=` details + UI summary toasts + mocked e2e/security tests.
   - Phase279: queue declined async task-center standard paging governance (`skipCount/maxItems/paging.totalItems/hasMoreItems`, `limit` backward-compat) + frontend page-size/prev-next controls + mocked e2e/security tests.
   - Phase280: queue declined async accepted/location governance (`202 + Location` for start/single-retry/bulk-retry-selected across non-requeue + requeue dry-run) + controller security assertions + mocked e2e async contract update.
   - Phase281: queue declined async contract discoverability + polling stability governance (OpenAPI `202/Location` response annotations + Preview Diagnostics adaptive task-center polling backoff `2s..15s`) + lint/build/mock e2e verification.
   - Phase282: async lifecycle governance parity expansion to rendition/recovery task centers (`TIMED_OUT/EXPIRED` + `startedAt/updatedAt/timeoutAt/expiresAt/createdBy/updatedBy` + lifecycle refresh transitions) + frontend task-center status/summary/metadata alignment + backend/frontend/mocked-e2e verification.

### Stream A - Failure ledger + lifecycle reset

1. Persist failure ledger by `(documentId, renditionType)` with `failedAt`, `failureCount`, `lastReason`.
2. Auto-clear ledger on successful rendition.
3. Auto-clear stale ledger on content/version change.
4. APIs: list failed ledger entries, reset by id, batch reset.

### Stream B - Queue dedup + async execution governance

1. Queue dedup key: `(documentId, renditionType)` for active tasks.
2. Extend async state machine: `QUEUED/RUNNING/COMPLETED/CANCELLED/FAILED/DECLINED`.
3. Add “declined” semantics for transient scheduling refusal.
4. Add cancellation protocol contract for active operations.

### Stream C - Capability preflight + pipeline selection

1. Preflight resolver before queueing:
   - transformer availability
   - source size threshold
   - mime-based route selection
2. Surface preflight skip reasons in dry-run breakdown and CSV export.
3. Add policy profile mapping for fallback pipeline chains.

### Stream D - Rendition validity hardening

1. Bind rendition output with source content hash.
2. Reject/clean zero-byte or stale-hash renditions.
3. Enforce hash-check on read path before serving preview.
4. Add repair operation: invalidate stale rendition and requeue.

## Verification gates (daily)

1. Backend:
   - controller + service tests
   - security tests for all admin endpoints
2. Frontend:
   - lint + build
   - mocked e2e for new governance paths
3. Stability:
   - no regression on existing phase235 mock e2e gate
   - release-note + docs index update each day

## Exit criteria

1. All four streams have API + UI + tests + verification docs.
2. Batch retries are deduplicated, auditable, and cancel-governed.
3. Preview artifacts are hash-safe and stale rendition leakage is blocked.
4. Operational dashboards expose queue health, failure ledger, and policy effectiveness.
