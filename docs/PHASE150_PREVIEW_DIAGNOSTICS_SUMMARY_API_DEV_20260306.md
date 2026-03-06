# Phase 150: Preview Diagnostics Summary API (Development)

## Date
2026-03-06

## Goal
- Add an admin API that summarizes preview failures with explicit sample confidence so operators can quickly understand failure distribution quality.

## Scope
- Backend only (`ecm-core`):
  - `GET /api/v1/preview/diagnostics/failures/summary`
  - Aggregation DTOs for status/category/top reasons
  - Sample confidence metadata
  - Repository count method support
  - Controller security/behavior tests

## Design
1. Keep existing `/failures` list endpoint unchanged for row-level triage.
2. Add summary endpoint that uses:
   - `countByDeletedFalseAndPreviewStatusIn(...)` for global failed total
   - `findRecentPreviewFailures(..., PageRequest.of(0, sampleLimit))` for sampled aggregation
3. Return confidence metadata:
   - `confidenceLevel=HIGH`, `confidenceReason=sample_complete` when sampled set covers total
   - `confidenceLevel=LOW`, `confidenceReason=sample_truncated` when sampled set is truncated
4. Return structured buckets:
   - `statusCounts`
   - `categoryCounts` (includes `retryable` signal for `TEMPORARY`)
   - `topReasons` (top 10, deterministic order)
5. Keep endpoint admin-only under existing class-level `@PreAuthorize("hasRole('ADMIN')")`.

## Changed Files
- `ecm-core/src/main/java/com/ecm/core/controller/PreviewDiagnosticsController.java`
- `ecm-core/src/main/java/com/ecm/core/repository/DocumentRepository.java`
- `ecm-core/src/test/java/com/ecm/core/controller/PreviewDiagnosticsControllerSecurityTest.java`

## Compatibility
- Backward compatible:
  - Existing list endpoint contract unchanged.
  - New summary endpoint is additive.
