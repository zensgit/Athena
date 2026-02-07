# Mail Automation P22 — Diagnostics Share Link Copy

Date: 2026-02-06

## Goal
Allow operators to quickly share the exact diagnostics context with teammates.

## Design
- Add `Copy link` action in Recent Mail Activity toolbar.
- Link content includes:
  - current path,
  - current diagnostics query params (already synced in P21),
  - `#diagnostics` anchor for direct section focus.
- Show toast feedback for copy success/failure.

## Files Changed
- `ecm-frontend/src/pages/MailAutomationPage.tsx`
