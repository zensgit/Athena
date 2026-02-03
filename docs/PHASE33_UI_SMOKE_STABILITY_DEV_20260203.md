# Phase 33 UI Smoke Stability (Dev)

## Goal
Stabilize the UI smoke run under dev-server conditions by removing TypeScript overlay failures and making the PDF search/download assertion resilient to search-result card rendering differences.

## Changes
- Admin dashboard audit export guard now imports `Alert` to prevent a dev-server overlay (TS2552) from blocking UI navigation.
- UI smoke PDF search/download step now locates the result card via `.MuiCard-root` + `hasText` rather than relying on heading role mapping, reducing false negatives when typography role mapping or truncation changes.

## Files
- `ecm-frontend/src/pages/AdminDashboard.tsx`
- `ecm-frontend/e2e/ui-smoke.spec.ts`

## Notes
- Dev-server overlay blocking clicks is handled by hiding/removing the overlay in the login helper; this phase ensures no new type errors are emitted during smoke runs.
