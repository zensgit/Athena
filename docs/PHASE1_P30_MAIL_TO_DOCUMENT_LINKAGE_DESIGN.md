# Mail Automation P30 — Mail-to-Document/Search Linkage

Date: 2026-02-06

## Goal
Enable fast navigation from diagnostics items to related document and search context.

## Design
### Frontend
- Processed messages table now includes `Linked Doc` column:
  - Open linked document action.
  - Find-similar action for linked document.
- Mail Documents table now includes `Search` action:
  - Navigates to search page with `similarSourceId` state.
- Added explicit accessible labels for action buttons:
  - `find similar documents`
  - `open mail document`
  - `open linked document`
  - `find similar linked document`

### Mapping Logic
- Build in-memory map from diagnostics `recentDocuments` by `(accountId, uid)`.
- Resolve processed row to linked document via same key.

## Files Changed
- `ecm-frontend/src/pages/MailAutomationPage.tsx`
- `ecm-frontend/e2e/mail-automation.spec.ts`
