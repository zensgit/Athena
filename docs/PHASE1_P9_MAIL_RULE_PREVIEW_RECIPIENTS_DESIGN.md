# Mail Automation P9 — Rule Preview Recipients

Date: 2026-02-06

## Goal
Improve rule preview diagnostics by showing the recipient list, helping validate `To` filters.

## Design
- Add a “To” column in the rule preview table.
- Display `recipients` from the preview API, fallback to `-` when empty.

## Files Changed
- `ecm-frontend/src/pages/MailAutomationPage.tsx`
