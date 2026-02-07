# Mail Automation P8 — Rule Priority Reorder

Date: 2026-02-06

## Goal
Make mail rule ordering easier to manage by providing quick move up/down controls that adjust rule priorities, similar to ordered mail rules in reference systems.

## Design
- Sort mail rules by `priority` (then name) for display.
- Add Move Up / Move Down buttons in the rule actions column.
- When clicked, swap priority values with the adjacent rule. If priorities are equal, nudge the moving rule by ±1 to ensure visible ordering.
- Persist priority changes via existing mail rule update endpoint.

## Files Changed
- `ecm-frontend/src/pages/MailAutomationPage.tsx`
