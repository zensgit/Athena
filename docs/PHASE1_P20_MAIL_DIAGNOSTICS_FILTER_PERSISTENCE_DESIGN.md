# Mail Automation P20 — Diagnostics Filter Persistence

Date: 2026-02-06

## Goal
Reduce repetitive setup by restoring Diagnostics filters after page refresh or revisit.

## Design
- Persist current diagnostics filters in `localStorage` under `mailDiagnosticsFilters`.
- Restored fields:
  - account
  - rule
  - status
  - subject
  - processedFrom
  - processedTo
- Add bootstrap guard (`diagnosticsFiltersLoaded`) to avoid premature diagnostics fetch before local filters are restored.

## Files Changed
- `ecm-frontend/src/pages/MailAutomationPage.tsx`
