# Mail Automation P15 — Bulk Reindex Rule Priorities

Date: 2026-02-06

## Goal
Reduce manual effort when many rules need stable ordering by adding one-click priority reindex for selected rules.

## Design
- Reuse existing multi-select state from P13.
- Add `Reindex selected` action in the rule toolbar.
- Reindex strategy:
  - keep selected rule order as shown in current sorted list,
  - take first selected priority as base,
  - assign priorities in steps of `10`.
- Persist via existing `updateRule` API per selected rule.
- Show summary toast for success/partial failure.

## Files Changed
- `ecm-frontend/src/pages/MailAutomationPage.tsx`
