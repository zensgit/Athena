# Phase 6 P1 Mail Automation Activity Tables - Development

## Summary
Improve the processed and documents activity tables with quick status filters, retention placement, and document open actions.

## Scope
- Add quick status filter chips for processed message status.
- Reposition retention summary under the processed messages header.
- Add an "Open" action for mail documents to jump to their folder.

## Implementation
- Added status filter chips that drive the diagnostics status filter.
- Moved retention chips into a dedicated summary row for clarity.
- Added an open-in-folder action that resolves the document parent and navigates to browse.

## Files Changed
- `ecm-frontend/src/pages/MailAutomationPage.tsx`
