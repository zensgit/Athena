# Phase 1 P53 - App Error Boundary Global Runtime Guard Design (2026-02-07)

## Background
- Existing `AppErrorBoundary` only handled React render/lifecycle throw paths.
- Runtime errors outside render flow (`window.error`, `unhandledrejection`) could still lead to blank/half-broken screens.

## Goal
- Reuse the existing fallback UI to handle global runtime errors and unhandled promise rejections.
- Keep changes minimal and frontend-only.

## Scope
- Frontend:
  - `ecm-frontend/src/components/layout/AppErrorBoundary.tsx`
  - `ecm-frontend/src/components/layout/AppErrorBoundary.test.tsx`

## Design Changes

### 1) Global listener lifecycle in boundary
- Added listeners in `componentDidMount`:
  - `window.addEventListener('error', ...)`
  - `window.addEventListener('unhandledrejection', ...)`
- Removed both listeners in `componentWillUnmount` to avoid leaks.

### 2) Runtime fallback transition
- Added `handleWindowError` and `handleUnhandledRejection` handlers.
- On first runtime failure:
  - set state `hasError=true`
  - set detail `message` from error/reason when available
  - render existing boundary fallback card instead of leaving unstable screen state

### 3) Logging
- Added targeted console error logs:
  - `AppErrorBoundary caught global runtime error`
  - `AppErrorBoundary caught unhandled promise rejection`

### 4) Test coverage
- Added unit tests to validate fallback UI is shown for:
  - dispatched `window error`
  - dispatched `unhandledrejection`

## Compatibility
- No API/backend/schema changes.
- Existing render-throw boundary behavior preserved.
