# Phase 1 P41: Error Boundary E2E + Advanced Search URL State Design

## Date
2026-02-07

## Background

After P40:

1. Global `AppErrorBoundary` existed, but there was no UI E2E proving users can recover to login from fallback page.
2. Advanced Search preview status filtering was available, but state was not encoded in URL, so share/reload continuity was weak.

## Scope

- `ecm-frontend/src/App.tsx`
- `ecm-frontend/src/components/layout/AppErrorBoundary.tsx`
- `ecm-frontend/src/pages/AdvancedSearchPage.tsx`
- `ecm-frontend/e2e/p1-smoke.spec.ts`
- `ecm-frontend/e2e/search-preview-status.spec.ts`

## Design Decisions

1. Add deterministic E2E crash trigger path for browser automation only

- `App.tsx` now checks:
  - `navigator.webdriver === true`
  - `localStorage.ecm_e2e_force_render_error === '1'`
- When both are true, app throws a controlled error to exercise `AppErrorBoundary`.
- This is isolated to webdriver sessions and does not affect normal users.

2. Ensure fallback "Back to Login" can recover test state

- `AppErrorBoundary` now clears `ecm_e2e_force_render_error` before redirecting to `/login`.
- This makes recovery deterministic for UI automation and mirrors expected user recovery flow.

3. Persist Advanced Search state in URL

- Added URL sync for:
  - `q`
  - `page`
  - `previewStatus` (comma-separated)
- Added mount-time URL restore:
  - Parse URL params
  - Restore query and preview status selection
  - Re-run search using restored query/page

4. Keep existing preview retry controls and behavior

- Retry actions remain unchanged.
- URL sync only improves deep-link/share/reload continuity.

## Expected Outcome

- E2E can reliably verify fallback recovery from render failure to login page.
- Advanced Search links preserve query/page/preview-status context and survive reload.

