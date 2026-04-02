# Phase367K FileList Checkout Actions

## Goal

Turn Athena's browse checkout semantics from passive diagnostics into direct operator actions by adding `Check Out` and `Cancel Checkout` to the `FileList` context menu.

## Scope

Frontend-only, reusing existing backend endpoints:

- `POST /api/v1/documents/{documentId}/checkout`
- `POST /api/v1/documents/{documentId}/cancel-checkout`

Implemented changes:

- add `nodeService.checkoutDocument()`
- add `nodeService.cancelCheckoutDocument()`
- add context-menu `Check Out` action for writable document rows that are not already checked out
- add context-menu `Cancel Checkout` action for writable checked-out document rows
- use checkout owner/admin semantics for disable reasons
- refresh current folder after successful action so browse state stays consistent

## Action Rules

### Check Out

Visible when:

- node is a document
- user has write-level UI access
- node is not currently checked out

Disabled when:

- a foreign active lock blocks checkout

### Cancel Checkout

Visible when:

- node is a document
- user has write-level UI access
- node is currently checked out

Disabled when:

- node is checked out by another user and current user is not admin

## Design Notes

- this phase intentionally avoids browse-level `Check In`, because `checkin` still requires new-version file flow and would force a heavier dialog/UI slice
- `refreshCurrentFolder()` is reused so the state transition is immediately visible in badges and menu rules
- checkout reasons stay inside `fileCheckoutBadgeUtils` to keep `FileList` uncluttered

## Files

- `ecm-frontend/src/services/nodeService.ts`
- `ecm-frontend/src/components/browser/FileList.tsx`
- `ecm-frontend/src/utils/fileCheckoutBadgeUtils.ts`
- `ecm-frontend/src/utils/fileCheckoutBadgeUtils.test.ts`

## Risk

- browse layer still does not offer a dedicated `Check In` upload dialog
- action results are currently surfaced with toast + folder refresh, not optimistic state mutation
