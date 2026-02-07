# Mail Automation P23 — Diagnostics Active Filter Chips

Date: 2026-02-06

## Goal
Improve diagnostics filtering efficiency with one-click removal of individual active filters.

## Design
- Add `Active filters` chip row in Recent Mail Activity.
- Show chips only for currently active diagnostics filters:
  - account
  - rule
  - status
  - subject
  - processed from
  - processed to
- Each chip has delete action to clear only that field.
- Keep existing `Clear filters` for full reset.

## Files Changed
- `ecm-frontend/src/pages/MailAutomationPage.tsx`
