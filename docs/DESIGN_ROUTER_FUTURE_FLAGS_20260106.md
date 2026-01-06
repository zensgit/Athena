# Design: Router Future Flags (2026-01-06)

## Goal
- Silence React Router v7 future-flag warnings and align app/test routers with upcoming behavior.

## Approach
- Enable `v7_startTransition` and `v7_relativeSplatPath` on the BrowserRouter in `App.tsx`.
- Enable the same flags on the test MemoryRouter in `MainLayout.menu.test.tsx`.

## Files
- ecm-frontend/src/App.tsx
- ecm-frontend/src/components/layout/MainLayout.menu.test.tsx
