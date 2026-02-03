# Phase 24 Mail Automation Dashboard Summary (2026-02-03)

## Goal
Expose the latest mail fetch summary on the Admin Dashboard for at-a-glance operational status.

## Changes
### Admin Dashboard
- Added a Mail Automation summary card that shows:
  - Accounts attempted / total
  - Skipped accounts, account errors
  - Messages found / matched / processed / skipped / errors
  - Fetch duration
  - Last fetched timestamp
- Added refresh action to reload summary and a shortcut to the Mail Automation page.

### Service
- Reused existing `/api/v1/integration/mail/fetch/summary` endpoint via `mailAutomationService`.

## Files
- `ecm-frontend/src/pages/AdminDashboard.tsx`
