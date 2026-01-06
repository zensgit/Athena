# Design: App Test Suspense Act Warning (2026-01-06)

## Goal
- Remove React Suspense act(...) warning in the App smoke test.

## Approach
- Mock the lazy-loaded `SearchDialog` to a synchronous test component.
- Await the mocked component render to ensure Suspense resolves inside the test lifecycle.

## Files
- ecm-frontend/src/App.test.tsx
