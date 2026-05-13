# Transfer Receiver Root Folder Picker Design and Verification

## Context

`PHASE369BP_TRANSFER_RECEIVER_REGISTRY_OPERATOR_SURFACE_VERIFICATION_20260409.md`
left one operator-facing limitation after the receiver registry surface shipped:
root folder entry was still manual UUID input.

This slice closes that limitation without changing the backend contract. Receiver
registry create/update requests still send `rootFolderId`; the frontend now lets
operators populate that value from the existing folder tree picker.

## Design

- Reuse `components/browser/FolderTree` with `variant="picker"` inside the
  existing `TransferReplicationPage` receiver dialog.
- Keep the `Root Folder ID` text field editable for remote or pre-known folder
  IDs and for backwards-compatible operator workflows.
- Accept only `nodeType === "FOLDER"` selections from the picker. Non-folder
  selections are ignored before they can mutate `receiverForm.rootFolderId`.
- Preserve the current receiver registry service API and DTO shape. No backend,
  migration, or API path change is required.
- Show the selected folder name in the text-field helper text when the picker
  provides one, while still storing the UUID as the submitted value.

## Files Changed

- `ecm-frontend/src/pages/TransferReplicationPage.tsx`
- `ecm-frontend/src/pages/TransferReplicationPage.test.tsx`

## Verification

### Targeted UI Test

```bash
cd ecm-frontend
CI=true npm test -- --runTestsByPath src/pages/TransferReplicationPage.test.tsx --watchAll=false
```

Result:

- 1 suite passed
- 3 tests passed
- New coverage verifies that a document selection is ignored and a folder
  selection is submitted as `rootFolderId` in `createReceiver(...)`.

### Frontend Lint

```bash
cd ecm-frontend
npm run lint
```

Result: passed.

### Production Build

```bash
cd ecm-frontend
CI=true npm run build
```

Result: compiled successfully. CRA still reports the existing bundle-size
advisory; no build failure was produced.

### Remote CI

Run: `25781971742`

Commit: `efbafc1 feat(transfer): add receiver root folder picker`

Result: passed.

- Backend Verify: passed
- Frontend Build & Test: passed
- Phase C Security Verification: passed
- Phase 5 Mocked Regression Gate: passed
- Frontend E2E Core Gate: passed
- Property Encryption Closeout Gate: passed
- Acceptance Smoke (3 admin pages): passed

## Residual Work

- Transfer receiver diagnostics remain summarized fields rather than a full
  audit-history view. That is still a separate scope from root-folder selection.
- Transfer target folder selection remains a manual field because ATHENA_HTTP
  targets may reference remote folder UUIDs; changing that requires a separate
  local-vs-remote target UX decision.
