# Phase 98: AppErrorBoundary Global Noise Filtering

## Date
2026-02-24

## Background
- `AppErrorBoundary` listens to global `error` and `unhandledrejection`.
- In real browsers, non-fatal runtime noise (for example `ResizeObserver` loop warnings or canceled/aborted async work) may surface globally.
- Treating every global signal as fatal can cause false-positive fallback pages.

## Goals
1. Ignore known non-fatal global runtime noise.
2. Keep fatal runtime exceptions routed to fallback UI.
3. Add unit tests for ignore paths and preserve existing crash coverage.

## Changes

### 1) Global noise filtering in `AppErrorBoundary`
- File: `ecm-frontend/src/components/layout/AppErrorBoundary.tsx`
- Added:
  - `IGNORED_GLOBAL_ERROR_PATTERNS`
    - `ResizeObserver loop limit exceeded`
    - `ResizeObserver loop completed with undelivered notifications`
  - `isAbortLikeReason(reason)`
    - filters `AbortError`, canceled/cancelled/aborted messages, and `ERR_CANCELED`.
  - `shouldIgnoreGlobalRuntimeIssue(message, reason)`
- Behavior update:
  - `handleWindowError` and `handleUnhandledRejection` now skip fallback transition when issue is recognized as non-fatal noise.
  - ignored cases log warning diagnostics instead of escalating to fatal fallback.

### 2) Unit coverage
- File: `ecm-frontend/src/components/layout/AppErrorBoundary.test.tsx`
- Added tests:
  - ignores `ResizeObserver` global error signal.
  - ignores abort-like unhandled rejection.
- Existing tests for fatal child throw/window error/unhandled rejection remain intact.

## Impact
- No backend/API changes.
- Reduces false unexpected-error pages caused by known benign browser/runtime noise.
