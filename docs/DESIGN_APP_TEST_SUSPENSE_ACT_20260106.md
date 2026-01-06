# Design: App Test Suspense Act Warning (2026-01-06)

## Goal
- Remove React Suspense act(...) warning in the App smoke test.

## Approach
- Mock the lazy-loaded `SearchDialog` to a synchronous test component.
- Push the route to `/login` to avoid auth redirects in the smoke test.
- Assert the login heading and sign-in button to confirm the UI renders.

## Files
- ecm-frontend/src/App.test.tsx
