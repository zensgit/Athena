# Phase367Q: Advanced Search Result Check-In Dialog

## Goal

Close the remaining search-result checkout lifecycle gap by adding a lightweight `Check In` flow to `AdvancedSearchPage`, reusing the existing backend `checkin` endpoint and the same operator semantics already introduced in browse.

## Scope

- Extend `advancedSearchActionUtils` with caller-relative `Check In` gating.
- Add a `Check In` action to result cards for checked-out documents.
- Add a lightweight dialog to support:
  - optional new version file upload
  - version comment
  - major/minor version toggle
  - `keepCheckedOut` when a new file is supplied
- Refresh current search results after a successful check-in so chips and actions stay aligned.

## Design

### Action gating

`getAdvancedSearchCheckInActionReason(...)` mirrors the browse-layer semantics:

- checkout owner: allowed
- admin: allowed
- other users: disabled with explicit owner-aware reason

This keeps search-result affordances aligned with backend authorization and with the existing `Check Out / Cancel Checkout` result actions.

### UI shape

The dialog is intentionally local to `AdvancedSearchPage`:

- no new shared component yet
- reuses the same `nodeService.checkinDocument(...)` contract as browse
- keeps the slice low-conflict in a page that already owns result-card actions

### Submission semantics

- file optional: release checkout without creating a new version
- `keepCheckedOut` requires a new file
- mutation success is preserved even if result refresh later fails

## Files

- `ecm-frontend/src/pages/AdvancedSearchPage.tsx`
- `ecm-frontend/src/utils/advancedSearchActionUtils.ts`
- `ecm-frontend/src/utils/advancedSearchActionUtils.test.ts`

## Claude Code

Claude Code was used as a parallel design assistant to validate that the smallest next search-result slice was a local `Check In` dialog reusing the existing backend endpoint and the new browse-layer semantics. Final implementation and validation were completed in this workspace.
