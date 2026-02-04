# Phase 37 Mail Diagnostics Clear Filters (Dev)

## Goal
Add a quick reset action for Mail Automation diagnostics filters to reduce manual cleanup after targeted searches.

## Changes
- Added "Clear filters" button in the Recent Mail Activity diagnostics toolbar.
- Button resets Account, Rule, Status, and Subject filters.
- Button is disabled when no filters are active.

## Files
- `ecm-frontend/src/pages/MailAutomationPage.tsx`
- `ecm-frontend/e2e/mail-automation.spec.ts`
