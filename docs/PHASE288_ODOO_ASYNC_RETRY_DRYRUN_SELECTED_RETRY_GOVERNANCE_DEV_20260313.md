# Phase 288 - Odoo Async Retry Dry-run Selected Retry Governance (Dev)

## Date
- 2026-03-13

## Goal
- Deliver selected-retry governance from dry-run candidate panels in two async task centers:
  - preview rendition resources async export center
  - ops recovery history async export center
- Reuse existing retry APIs (`retry-terminal/by-task-ids`) and existing dry-run APIs from Phase287.
- Provide operator-safe UX for selective retries instead of all-or-nothing terminal bulk retry.

## Odoo / Alfresco Benchmark Mapping
- Alfresco-oriented operator governance emphasizes:
  - pre-check first (dry-run),
  - selective execution on approved subset,
  - deterministic audit-friendly outcomes for batch operations.
- Phase288 parity/surpass points:
  - candidate-level selection directly from dry-run diagnostics panel.
  - one-click selected retry against existing task-id batch retry APIs.
  - explicit separation between planning (`dry-run`) and execution (`selected retry`).

## Implementation Scope
- Frontend only (reuse existing backend APIs):
  - `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`
  - optional service typing refinements in:
    - `ecm-frontend/src/services/previewDiagnosticsService.ts`
    - `ecm-frontend/src/services/opsRecoveryService.ts`
- Existing backend APIs consumed:
  - rendition:
    - `POST /api/v1/preview/diagnostics/renditions/resources/export-async/retry-terminal/dry-run`
    - `POST /api/v1/preview/diagnostics/renditions/resources/export-async/retry-terminal/by-task-ids`
  - ops:
    - `POST /api/v1/ops/recovery/history/export-async/retry-terminal/dry-run`
    - `POST /api/v1/ops/recovery/history/export-async/retry-terminal/by-task-ids`

## Planned UX Behavior
- Dry-run panel candidate table supports row selection by source task id.
- Panel actions:
  - `Select All Retryable`
  - `Clear Selection`
  - `Retry Selected`
- Selection rules:
  - only candidates with retryable dry-run outcome are selectable.
  - duplicate/empty task ids are normalized before submission.
  - selection is reset when dry-run is refreshed, status/type filter changes, or page context changes.
- Retry result feedback:
  - toast summary includes `retried/reused/skipped/failed`.
  - panel refreshes task center list after selected retry completes.

## Governance and Safety Notes
- No new backend mutation surface added in Phase288; execution uses existing guarded APIs.
- Existing backend caps on selected ids remain authoritative.
- Dedup semantics remain active: selected retry may return `REUSED` when equivalent active tasks already exist.

## Risks and Mitigations
- Risk: stale dry-run snapshot after task state changes.
  - Mitigation: clear/refresh dry-run result after execution and on filter changes.
- Risk: selecting non-retryable or terminal-drifted tasks.
  - Mitigation: frontend disables non-retryable rows; backend keeps final terminal validation.
- Risk: oversized operator selection.
  - Mitigation: frontend dedup + cap-aware messaging; backend hard limit enforcement.

## Out of Scope
- New backend endpoints for selected retry (already available from Phase285/287).
- Cross-center unified selected-retry orchestration dashboard (future phase).
