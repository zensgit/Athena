# Phase 9 – E2E Stability + Mail Automation UX

## Scope
- Stabilize UI smoke upload flows and version history assertions in E2E.
- Make Mail Automation folder listing resilient when zero folders are returned.

## Changes
- **Upload dialog close handling (E2E)**
  - Updated `uploadViaDialog` to click the dialog close button when upload completes, with an Escape fallback.
  - File: `ecm-frontend/e2e/ui-smoke.spec.ts`.
- **Version history header assertion (E2E)**
  - Tightened column header selector to avoid tooltip text collisions.
  - File: `ecm-frontend/e2e/ui-smoke.spec.ts`.
- **Mail Automation folders empty state (UI)**
  - Track whether folder listing was attempted and show a helpful empty state when zero folders are returned.
  - Reset folder list on account change to avoid stale display.
  - File: `ecm-frontend/src/pages/MailAutomationPage.tsx`.

## Notes
- These changes are scoped to UI feedback and test reliability; no backend/API changes.
