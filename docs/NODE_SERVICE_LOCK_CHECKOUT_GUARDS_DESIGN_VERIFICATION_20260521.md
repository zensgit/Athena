# nodeService lock/checkout Shape Guards: Design and Verification

## Context

This is the next incremental `nodeService` response-shape guard slice after
folder/node CRUD. It preserves endpoints, request bodies, query params, and
existing UI error handling while adding runtime validation for lock and checkout
JSON responses.

## Scope

In scope:

- `getLockInfo`
- `lockNodeTyped`
- `getCheckoutInfo`
- `getCheckoutLineage`
- `checkoutDocument`
- `cancelCheckoutDocument`
- `checkinDocument`

Out of scope:

- `unlockNode`
- `unlockNodeDeep`

The two unlock methods return `void` from the backend and remain outside JSON
response-shape validation.

## Design

The slice reuses the existing service-wide `NODE_UNEXPECTED_RESPONSE_MESSAGE`
and helper bundle. No new sentinel or response style was introduced.

New guards:

- `isLockInfo`
- `isCheckoutInfo`
- `isCheckoutLineageVersionResponse`
- `isCheckoutLineageResponse`

New normalization wrappers:

- `assertAndNormalizeLockInfo`
- `assertAndNormalizeCheckoutInfo`
- `assertAndNormalizeCheckoutLineageResponse`

Timestamp handling follows the folder/node CRUD rule:

- ISO/date strings pass through unchanged.
- Jackson `LocalDateTime` arrays are normalized into string form before UI
  consumers see them.
- `null` and `undefined` pass through for nullable or optional fields.

Mutation responses from `checkoutDocument`, `cancelCheckoutDocument`, and
`checkinDocument` now reuse `assertAndNormalizeApiNodeDetailsResponse` before
the existing `apiNodeDetailsToNode` mapper.

`checkinDocument` remains multipart. The implementation preserves:

- Optional `file`.
- Trimmed non-blank `comment`.
- Omission of blank comments.
- String payloads for `majorVersion` and `keepCheckedOut`.
- Existing multipart content-type header.

## Consumer Behavior

Existing consumer behavior is preserved:

- `DocumentPreview` treats lock and checkout info as best-effort diagnostics.
- Checkout, cancel-checkout, check-in, and lock actions already show generic
  error toasts on failure.
- `VersionHistoryDialog` clears checkout relation/graph/lineage when lineage
  loading fails.

Malformed JSON now fails earlier with the shared node-service sentinel instead
of flowing into UI state.

## Verification

Targeted Jest:

```bash
cd ecm-frontend
CI=true npm test -- --runTestsByPath src/services/nodeService.lockCheckout.test.ts --watchAll=false
```

Result:

- 1 suite passed.
- 10 tests passed.

Node-service regression sweep:

```bash
cd ecm-frontend
CI=true npm test -- --runTestsByPath \
  src/services/nodeService.lockCheckout.test.ts \
  src/services/nodeService.folderNodeCrud.test.ts \
  src/services/nodeService.searchProper.test.ts \
  src/services/nodeService.previewSide.test.ts \
  src/services/nodeService.batchDownloadAsync.test.ts \
  src/services/nodeService.relationsRenditions.test.ts \
  src/services/nodeService.createFolder.test.ts \
  src/services/nodeService.recordProjection.test.ts \
  --watchAll=false
```

Result:

- 8 suites passed.
- 64 tests passed.

Additional checks:

- `CI=true npm test -- --runTestsByPath src/components/preview/DocumentPreview.undeclare.test.tsx --watchAll=false`:
  passed, 1 test.
- `npm run lint`: passed.
- `CI=true npm run build`: passed. CRA emitted only the existing bundle-size
  advisory and `fs.F_OK` deprecation warning.
- `git diff --check -- . ':!.env'`: clean.

## Notes

The slice deliberately does not start the remaining `nodeService` subdomains:

- version/history
- permissions

## CI Follow-Up

GitHub Actions run `26209237809` for commit `3847e46` completed successfully.

All seven CI jobs passed:

- Backend Verify
- Frontend Build & Test
- Phase C Security Verification
- Acceptance Smoke (3 admin pages)
- Property Encryption Closeout Gate
- Phase 5 Mocked Regression Gate
- Frontend E2E Core Gate
