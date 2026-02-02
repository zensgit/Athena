# Phase 11 - Mail Automation (UI) Enhancements

## Summary
- Added richer account telemetry: next poll countdown, retry-suggested indicator, and OAuth env prefix with copy shortcut.
- Added diagnostics filter visibility with quick clear actions in Recent Mail Activity.
- Resolved target folder IDs to readable paths/names in the Mail Rules table.
- Added a smoke assertion for the new "Next poll" UI in Playwright.

## Implementation Details
- Accounts table now shows:
  - `Next poll` status (overdue highlights in red).
  - Retry suggestion chip when last fetch failed.
  - OAuth env prefix with a copy-to-clipboard action.
- Diagnostics section now exposes active filters as removable chips with a "Clear all" button.
- Rules table resolves `assignFolderId` using `nodeService.getNode` and shows readable paths.

## Files Changed
- `ecm-frontend/src/pages/MailAutomationPage.tsx`
- `ecm-frontend/e2e/mail-automation.spec.ts`
