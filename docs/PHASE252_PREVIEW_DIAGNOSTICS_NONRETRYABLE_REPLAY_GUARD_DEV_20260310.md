# Phase 252 - Preview Diagnostics Non-retryable Replay Guard (Dev)

Date: 2026-03-10  
Scope: `ecm-frontend`

## 1. Goals

1. Prevent invalid replay operations for non-retryable failure reasons in Preview Diagnostics.
2. Keep governance semantics consistent with retryability: non-retryable supports clear-only actions.
3. Preserve existing retryable reason replay/clear flow.

## 2. Implementation

File: `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`

- Updated top-reasons action row:
  - `Replay DL` is now disabled when `entry.retryable === false`.
- `Clear DL` remains enabled for both retryable and non-retryable reason groups.

## 3. E2E Contract Extension

File: `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`

- Added assertions for unsupported reason row:
  - `Replay dead-letter ...` button is disabled.
  - `Clear dead-letter ...` remains executable.
- Added payload assertions:
  - clear-by-filter includes `category=UNSUPPORTED`, `retryable=false`
  - replay-by-filter never includes unsupported/non-retryable calls.

## 4. Design Notes

- This closes an operator UX gap where replay was visible for non-retryable reasons but had no practical recovery value.
- The change reduces unnecessary replay attempts and keeps action affordance aligned with policy semantics.
