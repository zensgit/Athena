# Transfer Replication Service Shape Guards Design and Verification

## Context

The Transfer Replication page now has picker support for receiver roots,
`LOOPBACK` target folders, and definition source folders. The remaining
cross-boundary risk in the same surface was the frontend service layer:
`transferReplicationService` trusted every `api.get/post/put` response shape.

If a mocked CI route or deployed nginx fallback returned SPA HTML for one of
the transfer endpoints, the page could receive a string instead of an array or
page response and crash during render. This slice makes that boundary fail
closed with a clear service error so the page can use its existing
`Failed to load transfer replication data` toast path.

## Design

- Add a shared `TRANSFER_REPLICATION_UNEXPECTED_RESPONSE_MESSAGE` for malformed
  or HTML-fallback responses.
- Guard list endpoints with array checks plus DTO shape predicates:
  `listTargets`, `listDefinitions`, and `listReceivers`.
- Guard mutation/readback endpoints with DTO shape predicates:
  target create/update/verify, definition create/update/run, job retry, and
  receiver create/update/verify.
- Guard `listJobs(...)` with a page-response predicate and per-job validation.
- Treat `entryReport` as object/null because the backend DTO exposes it as
  `Map<String,Object>` and the page already separately checks whether it is
  renderable.
- Keep delete methods unchanged because they intentionally do not consume a
  response body.
- Keep validation structural rather than enum-exhaustive so additive backend
  enum values do not break the UI.

## Files Changed

- `ecm-frontend/src/services/transferReplicationService.ts`
- `ecm-frontend/src/services/transferReplicationService.test.ts`

## Verification

### Targeted Service Test

```bash
cd ecm-frontend
CI=true npm test -- --runTestsByPath src/services/transferReplicationService.test.ts --watchAll=false
```

Result:

- 1 suite passed
- 13 tests passed
- New coverage rejects HTML fallback for target lists, job pages, and mutation
  responses; rejects malformed definition list items; accepts guarded target,
  definition, receiver, and job responses.

### Targeted Service and Page Tests

```bash
cd ecm-frontend
CI=true npm test -- --runTestsByPath \
  src/pages/TransferReplicationPage.test.tsx \
  src/services/transferReplicationService.test.ts \
  --watchAll=false
```

Result:

- 2 suites passed
- 18 tests passed
- Confirms the new service guards and the Transfer Replication page picker tests
  remain compatible.

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
advisory; no build failure was produced. The first build attempt caught an
over-broad `PageResponse<T>` cast; the final implementation returns an explicit
page-response object after guard checks.

### Diff Hygiene

```bash
git diff --check -- ecm-frontend/src/services/transferReplicationService.ts \
  ecm-frontend/src/services/transferReplicationService.test.ts \
  docs/TRANSFER_REPLICATION_SERVICE_SHAPE_GUARDS_DESIGN_VERIFICATION_20260513.md
```

Result: passed.

## Residual Work

- This does not add new transfer backend APIs or receiver diagnostic audit
  history.
- Other frontend services may still need similar shape guards; this slice only
  covers the Transfer Replication service used by the recently changed operator
  page.
