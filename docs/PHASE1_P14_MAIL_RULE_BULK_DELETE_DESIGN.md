# Mail Automation P14 — Bulk Delete Rules

Date: 2026-02-06

## Goal
Allow admins to delete multiple selected mail rules in one action, with clear confirmation and failure feedback.

## Design
- Reuse existing rule multi-select state introduced in P13.
- Add `Delete selected` action in rule toolbar.
- Require confirmation dialog (`window.confirm`) before deletion.
- Execute delete calls in parallel using existing `deleteRule` API.
- Update local rule list and selection state immediately for successful deletions.
- Show result summary toast:
  - all success,
  - partial success with failed count and sample rule names,
  - all failed.

## Files Changed
- `ecm-frontend/src/pages/MailAutomationPage.tsx`
