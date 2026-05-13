# Transfer Definition Source Folder Picker Design and Verification

## Context

Transfer Replication now has picker support for receiver roots and local
`LOOPBACK` target folders. The remaining high-friction field in the same
operator workflow was `Source Node ID` on replication definitions.

The source can be either a document or a folder. The existing `FolderTree`
component only represents folders, so this slice adds a folder picker while
keeping the manual text field for document node UUIDs and known IDs.

## Design

- Reuse `components/browser/FolderTree` with `variant="picker"` inside the
  replication definition dialog.
- Keep `Source Node ID` editable for document nodes and known UUIDs.
- Accept only `nodeType === "FOLDER"` selections from the picker. Non-folder
  selections are ignored before they can mutate `definitionForm.sourceNodeId`.
- Show the selected folder name in the helper text when the picker provides one.
- Preserve the existing `ReplicationDefinitionMutationRequest` contract. No
  backend, migration, or API path change is required.
- Harden the page test mock by explicitly restoring the
  `buildReplicationDefinitionRequest(...)` passthrough implementation in
  `beforeEach`; otherwise the isolated page test can call
  `createDefinition(undefined)` after mock reset.

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
- 5 tests passed
- New coverage verifies that a document selection is ignored and a folder
  selection is submitted as `sourceNodeId` in `createDefinition(...)`.

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
  docs/TRANSFER_DEFINITION_SOURCE_FOLDER_PICKER_DESIGN_VERIFICATION_20260513.md
```

Result: passed.

### Remote CI

Run: `25785229098`

Commit: `5517174 feat(transfer): add definition source folder picker`

Result: passed.

- Backend Verify: passed
- Frontend Build & Test: passed
- Phase C Security Verification: passed
- Phase 5 Mocked Regression Gate: passed
- Property Encryption Closeout Gate: passed
- Frontend E2E Core Gate: passed
- Acceptance Smoke (3 admin pages): passed

## Residual Work

- Document source node browsing remains manual because the existing reusable
  picker is folder-only. A rich document picker would need a separate reusable
  node-browser selection component.
- Remote `ATHENA_HTTP` target folder browsing remains manual because those UUIDs
  belong to a remote repository.
