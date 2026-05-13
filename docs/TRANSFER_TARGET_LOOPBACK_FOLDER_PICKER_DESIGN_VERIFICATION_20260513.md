# Transfer Target LOOPBACK Folder Picker Design and Verification

## Context

After the receiver registry root-folder picker shipped, the transfer target
dialog still required operators to paste a folder UUID manually. This was safe
for `ATHENA_HTTP` targets because their target folder IDs point at a remote
Athena repository, but it was unnecessary friction for `LOOPBACK` targets where
the destination folder is local and browsable.

This slice adds a local picker for `LOOPBACK` targets only. It deliberately
keeps the existing text field and leaves `ATHENA_HTTP` unchanged.

## Design

- Reuse `components/browser/FolderTree` with `variant="picker"` in the existing
  transfer target dialog.
- Show the picker only when `targetForm.transportType === "LOOPBACK"`.
- Keep `Target Folder ID` editable so operators can still paste known UUIDs.
- Ignore non-folder selections before they mutate `targetForm.targetFolderId`.
- Clear the selected folder display name when the operator manually edits the
  text field or switches the target to `ATHENA_HTTP`.
- Preserve the existing `TransferTargetMutationRequest` contract. No backend,
  migration, or API path change is required.

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
- 4 tests passed
- New coverage verifies that a document selection is ignored and a folder
  selection is submitted as `targetFolderId` in `createTarget(...)`.

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

### Diff Hygiene

```bash
git diff --check -- ecm-frontend/src/pages/TransferReplicationPage.tsx \
  ecm-frontend/src/pages/TransferReplicationPage.test.tsx \
  docs/TRANSFER_TARGET_LOOPBACK_FOLDER_PICKER_DESIGN_VERIFICATION_20260513.md
```

Result: passed.

## Residual Work

- `ATHENA_HTTP` target folder selection remains manual because the folder UUID
  belongs to the remote repository. A remote browse workflow would need a
  separate authenticated remote repository listing API.
- Receiver diagnostics still expose summarized state only; full audit history is
  intentionally outside this picker slice.
