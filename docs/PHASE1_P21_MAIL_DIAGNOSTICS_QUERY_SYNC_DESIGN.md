# Mail Automation P21 — Diagnostics Filters URL Query Sync

Date: 2026-02-06

## Goal
Make diagnostics filters shareable and refresh-safe by syncing filter state to URL query parameters.

## Design
- Add query mapping for diagnostics filters:
  - `dAccount`, `dRule`, `dStatus`, `dSubject`, `dFrom`, `dTo`
- On first page load:
  - restore from URL query first,
  - fallback to `localStorage` (`mailDiagnosticsFilters`) when query is empty.
- On filter updates:
  - write current values back to URL using `replace` navigation,
  - preserve unrelated query parameters.
- Keep existing localStorage persistence for offline/session continuity.

## Files Changed
- `ecm-frontend/src/pages/MailAutomationPage.tsx`
