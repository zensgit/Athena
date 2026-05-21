# nodeService Permissions Shape Guards: Design and Verification

## Context

This is the final planned `nodeService` response-shape guard subdomain after
version/history. It preserves existing endpoints, request bodies, query params,
void write semantics, and UI error handling while adding runtime validation for
permission-related JSON reads.

## Scope

In scope:

- `getPermissions`
- `getPermissionDiagnostics`
- `getPermissionSets`
- `getPermissionSetMetadata`

Out of scope:

- `setPermission`
- `applyPermissionSet`
- `setInheritPermissions`
- `removePermission`

Those four methods return `void` from the backend and remain outside JSON
response-shape validation. Tests still lock their endpoint and params shapes so
the slice does not accidentally change write behavior.

## Design

The slice reuses the service-wide `NODE_UNEXPECTED_RESPONSE_MESSAGE` and helper
bundle. No new sentinel or response style was introduced.

New guards:

- `isPermissionResponse`
- `isPermissionDecision`
- `isPermissionSetRecord`
- `isPermissionSetMetadata`

New normalization helper:

- `normalizePermissionResponse`
- `assertAndNormalizePermissionResponseArray`

`PermissionDto.expiryDate` can serialize as an ISO/date string, `null`, or a
Jackson `LocalDateTime` array. The guard accepts those shapes and normalizes
arrays into string form before `PermissionsDialog` groups and renders the ACL.

Runtime enum-like values are validated as strings rather than hard-coded enum
members. This follows the prior `nodeService` guard rule for wire unions:
reject malformed non-string values, but do not create a second source of truth
for backend enum membership.

`getPermissions` keeps the existing grouping behavior:

- `GROUP` authorities are keyed as `GROUP_<authority>`.
- Other authority types keep the authority as the key.
- Multiple permission rows for a principal remain grouped into one array.

Malformed JSON now fails with the shared node-service sentinel instead of
flowing into ACL table state, diagnostic state, or permission-set selectors.

## Consumer Behavior

Existing consumer behavior is preserved:

- `PermissionsDialog` catches permission-load failures and shows
  `Failed to load permissions`.
- Permission diagnostics failures set the existing diagnostics error state.
- Permission set and metadata loads already use `.catch(() => ({}))` and
  `.catch(() => [])`.
- `PermissionTemplatesPage` treats permission-set metadata as optional and
  falls back to an empty list.

Void write methods keep their existing toast/catch behavior and are not guarded.

## Verification

Targeted Jest:

```bash
cd ecm-frontend
CI=true npm test -- --runTestsByPath src/services/nodeService.permissions.test.ts --watchAll=false
```

Result:

- 1 suite passed.
- 5 tests passed.

Node-service regression sweep:

```bash
cd ecm-frontend
CI=true npm test -- --runTestsByPath \
  src/services/nodeService.permissions.test.ts \
  src/services/nodeService.versionHistory.test.ts \
  src/services/nodeService.lockCheckout.test.ts \
  src/services/nodeService.relationsRenditions.test.ts \
  src/services/nodeService.folderNodeCrud.test.ts \
  src/services/nodeService.searchProper.test.ts \
  src/services/nodeService.previewSide.test.ts \
  src/services/nodeService.batchDownloadAsync.test.ts \
  src/services/nodeService.createFolder.test.ts \
  src/services/nodeService.recordProjection.test.ts \
  --watchAll=false
```

Result:

- 10 suites passed.
- 77 tests passed.

Additional checks:

- `npm run lint`: passed.
- `CI=true npm run build`: passed. CRA emitted only the existing bundle-size
  advisory and `fs.F_OK` deprecation warning.
- `git diff --check -- . ':!.env'`: clean.

## Notes

This closes the planned JSON response-shape guard backlog for `nodeService`.
Blob, download, and void methods remain deliberate out-of-scope boundaries.

## CI Follow-Up

GitHub Actions run `26226880018` for commit `9976821` completed successfully.

All seven CI jobs passed:

- Backend Verify
- Frontend Build & Test
- Phase C Security Verification
- Acceptance Smoke (3 admin pages)
- Property Encryption Closeout Gate
- Phase 5 Mocked Regression Gate
- Frontend E2E Core Gate
