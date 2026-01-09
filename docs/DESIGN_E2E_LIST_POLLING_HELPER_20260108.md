# Design: E2E List Polling Helper (2026-01-08)

## Goal
- Stabilize list-driven E2E checks by reusing a shared API polling helper.

## Approach
- Add a generic `waitForListItem` helper that polls list endpoints returning `{ content: [...] }`.
- Reuse the helper for folder lookup, document lookup, and correspondent visibility checks.
- Keep poll settings configurable per call (max attempts, delay, description).

## Files
- `ecm-frontend/e2e/ui-smoke.spec.ts`
