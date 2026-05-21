# nodeService Version/History Shape Guards: Design and Verification

## Context

This is the next incremental `nodeService` response-shape guard slice after
lock/checkout. It preserves existing endpoints, request bodies, query params,
download behavior, and UI error handling while adding runtime validation for
version/history JSON responses.

## Scope

In scope:

- `getVersionHistory`
- `getVersionHistoryPage`
- `getVersionTextDiff`
- `revertToVersion`

Indirectly covered:

- `createVersion`, because it ignores the multipart check-in response and then
  reads the newest item through `getVersionHistory`.
- `downloadVersion`, because it remains a download method but uses guarded
  `getNode` and `getVersionHistory` lookups before calling `api.downloadFile`.

Out of scope:

- `downloadVersion` response body / blob download.
- Check-in multipart response shape inside `createVersion`.
- Permission APIs.

## Design

The slice reuses the service-wide `NODE_UNEXPECTED_RESPONSE_MESSAGE` and helper
bundle. No new sentinel or response style was introduced.

New or widened guards:

- `isStringOrTimestampArray`
- `isApiVersionResponse`
- `isVersionTextDiff`
- `isVersionCompareTextDiffResponse`

New shared mapping/normalization helpers:

- `normalizeApiVersionResponseTimestamps`
- `mapApiVersionResponseToVersion`
- `assertAndNormalizeApiVersionResponseArray`
- `assertAndNormalizeApiVersionPageResponse`

`ApiVersionResponse.createdDate` now accepts either an ISO/date string or a
Jackson `LocalDateTime` array and normalizes arrays into string form before
existing UI mappers see the value. `creator` accepts nullish backend values and
normalizes them to an empty string so the `Version` UI contract remains stringy
without rendering a misleading placeholder.

Existing version mappers in checkout lineage and relation versions now reuse
the same helper. This keeps version DTO behavior consistent across the
previously guarded subdomains and the new document version/history methods.

`getVersionTextDiff` validates only the `textDiff` envelope the frontend
consumes. Missing or null `textDiff` still returns the existing fallback:

```text
{ available: false, truncated: false, reason: 'No diff available', diff: null }
```

Malformed JSON now fails with the shared node-service sentinel instead of
flowing into version table state, diff state, or restore mapping.

## Consumer Behavior

Existing consumers already tolerate failures:

- `VersionHistoryDialog` catches version-history load failures and shows
  `Failed to load version history`.
- Loading older checkout-lineage versions is inside `try/catch` and shows
  `Failed to load checkout lineage versions`.
- Download and restore actions are inside `try/catch` and show their existing
  failure toasts.
- Text-diff load failures set the existing unavailable diff state rather than
  crashing the dialog.

## Verification

Targeted Jest:

```bash
cd ecm-frontend
CI=true npm test -- --runTestsByPath src/services/nodeService.versionHistory.test.ts --watchAll=false
```

Result:

- 1 suite passed.
- 8 tests passed.

Node-service regression sweep:

```bash
cd ecm-frontend
CI=true npm test -- --runTestsByPath \
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

- 9 suites passed.
- 72 tests passed.

Additional checks:

- `npm run lint`: passed.
- `CI=true npm run build`: passed. CRA emitted only the existing bundle-size
  advisory and `fs.F_OK` deprecation warning.

## Notes

The slice deliberately does not start the remaining `nodeService` subdomain:

- permissions
