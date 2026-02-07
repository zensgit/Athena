# Mail Automation P13 — Rule Filters and Bulk Toggle

Date: 2026-02-06

## Goal
Improve rule operations usability by adding fast filtering and bulk enable/disable in Mail Rules.

## Design
- Add rule filter controls above the rule table:
  - keyword search,
  - account filter,
  - status filter (`All/Enabled/Disabled`).
- Add row selection and select-all for currently filtered rules.
- Add bulk actions:
  - `Enable selected`
  - `Disable selected`
- Preserve existing per-rule actions and ordering controls.
- Keep backend unchanged by reusing `updateRule` endpoint for each selected rule.

## Files Changed
- `ecm-frontend/src/pages/MailAutomationPage.tsx`
