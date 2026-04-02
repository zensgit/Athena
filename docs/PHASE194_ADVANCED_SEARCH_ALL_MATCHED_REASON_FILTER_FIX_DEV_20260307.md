# Phase 194 - Advanced Search All-Matched Reason Filter Fix (Development)

## Date
2026-03-07

## Goal
- Fix false-empty target selection in "Retry all matched" flow when no explicit reason is selected.
- Ensure all-matched bulk retry queues retryable failures correctly without forcing `UNSPECIFIED` reason matching.

## Root cause
- In `collectMatchedRetryableFailedTargets(reason?)`, `reason` was normalized unconditionally.
- `normalizePreviewFailureReason(undefined)` returns `UNSPECIFIED`, which unintentionally filtered out normal failure reasons.
- Result: all-matched action often returned zero targets even when retryable failures existed.

## Implemented
- Updated `ecm-frontend/src/pages/AdvancedSearchPage.tsx`:
  - Changed reason normalization to nullable behavior:
    - `const normalizedReason = reason ? normalizePreviewFailureReason(reason) : null;`
  - Updated reason filter condition:
    - apply reason equality check only when `normalizedReason !== null`.

## Impact
- `Retry all matched (max 200)` now queues retryable failed previews as expected.
- Reason-specific all-matched actions continue to work with explicit reason matching.
