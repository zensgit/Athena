# Phase367P: FileList Check-In Dialog

## Goal

Close the last obvious browse-layer checkout lifecycle gap by adding a lightweight `Check In` flow to `FileList`, using the existing backend `POST /api/v1/documents/{documentId}/checkin` endpoint.

## Scope

- Add a frontend `checkinDocument(...)` service wrapper so browse/search callers do not need to misuse `createVersion(...)`.
- Extend file checkout action helpers with caller-relative `Check In` gating.
- Add a `Check In` context-menu action to `FileList` for checked-out documents.
- Add a lightweight dialog that supports:
  - optional new version file upload
  - version comment
  - major/minor version toggle
  - `keepCheckedOut` toggle when a new file is provided

## Design

### Service contract

`nodeService.checkinDocument(nodeId, options)` now wraps the existing backend multipart endpoint and accepts:

- `file?: File | null`
- `comment?: string`
- `majorVersion?: boolean`
- `keepCheckedOut?: boolean`

This keeps browse-layer check-in and future search-layer check-in on one shared contract.

### Operator semantics

`fileCheckoutBadgeUtils` now exposes `getCheckInActionReason(...)`:

- non-admin checkout owner: allowed
- admin: allowed
- other users: blocked with explicit owner-aware reason

This matches existing backend authorization while keeping the menu explanation caller-relative.

### Browse-layer UX

`FileList` now offers `Check In` in the context menu for checked-out documents.

The dialog intentionally stays light:

- file is optional, so operators can simply release checkout
- `keepCheckedOut` is only available when a file is selected
- success refreshes the current folder so browse-level badges and action gating stay in sync

## Why This Slice

This is the smallest safe step after browse/search `Check Out` and `Cancel Checkout`:

- it completes more of the checkout lifecycle
- it reuses an existing backend endpoint
- it avoids introducing a heavier working-copy model or a dedicated versioning page rewrite

## Files

- `ecm-frontend/src/services/nodeService.ts`
- `ecm-frontend/src/utils/fileCheckoutBadgeUtils.ts`
- `ecm-frontend/src/utils/fileCheckoutBadgeUtils.test.ts`
- `ecm-frontend/src/components/browser/FileList.tsx`

## Claude Code

Claude Code was used as a parallel design assistant to pressure-test the next lowest-conflict checkout slice. Final implementation and validation were completed in this workspace.
