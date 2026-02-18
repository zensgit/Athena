# Design: Route Fallback to Prevent Blank Page (2026-02-18)

## Background
- SPA had no catch-all route.
- Navigating to an unmatched path could render an empty screen (no route element), which looks like a blank page.

## Goal
- Ensure unknown routes always resolve to a valid screen instead of blank output.

## Scope
- Frontend routing only (`ecm-frontend/src/App.tsx`).
- No API or backend changes.

## Decision
1. Add router catch-all:
   - `Route path="*"` -> `Navigate to="/"`.
2. Keep existing auth flow:
   - `/` is still protected by `PrivateRoute`.
   - Unauthenticated users continue to land on `/login`.

## Why This Approach
- Minimal, low-risk change.
- Preserves existing route behavior.
- Eliminates unmatched-path blank-state class without introducing new pages/components.

## Files
- `ecm-frontend/src/App.tsx`
- `ecm-frontend/src/App.test.tsx`
