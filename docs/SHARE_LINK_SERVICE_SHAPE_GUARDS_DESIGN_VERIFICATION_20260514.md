# Share Link Service Shape Guards Design and Verification

## Context

The frontend hardening line continues to close service boundaries that can
mistake SPA HTML fallback or malformed JSON for valid API data.
`shareLinkService` is a high-risk remaining surface because it feeds:

- `ShareLinkManager` for node share-link listing, creation, lifecycle actions,
  access statistics, and access logs.
- `AdminDashboard` for admin-wide share-link listing and lifecycle actions.

Before this slice, share-link list/readback and audit endpoints trusted the API
body shape directly. A missing route or HTML fallback could flow into share-link
status chips, admin tables, or access-log rendering.

## Design

- Add a shared `SHARE_LINK_UNEXPECTED_RESPONSE_MESSAGE` for malformed share-link
  responses.
- Guard `ShareLink` responses:
  - required string `id`, `token`, `nodeId`, `nodeName`, `createdBy`, and
    `createdAt`;
  - nullable-string `expiryDate`, `name`, and `lastAccessedAt`;
  - nullable-number `maxAccessCount`;
  - required numeric `accessCount`;
  - required boolean `active`, `passwordProtected`, `hasIpRestrictions`, and
    `isValid`;
  - `permissionLevel` restricted to `VIEW`, `COMMENT`, or `EDIT`.
- Guard share-link arrays for node lists, my-link lists, and admin all-link
  lists.
- Guard `createLink`, `updateLink`, and `reactivateLink` readbacks with the same
  `ShareLink` validator.
- Guard access logs with nullable backend fields (`clientIp`, `userAgent`, and
  `failureReason`).
- Guard access statistics as strict numeric totals.
- Keep deactivate/delete endpoints unchanged because they are no-content
  endpoints and current consumers only await HTTP success/failure.

## Files Changed

- `ecm-frontend/src/services/shareLinkService.ts`
- `ecm-frontend/src/services/shareLinkService.test.ts`

## Verification

### Targeted Service Test

```bash
cd ecm-frontend
CI=true npm test -- --runTestsByPath src/services/shareLinkService.test.ts --watchAll=false
```

Result:

- 1 suite passed
- 12 tests passed
- New coverage rejects HTML fallback for share-link lists; rejects malformed
  share-link list items, create readbacks, access logs, and access statistics;
  accepts nullable backend audit fields; preserves deactivate/delete endpoint
  wiring.

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
advisory. Node emitted the known dependency deprecation warning for `fs.F_OK`;
it did not fail the build.

### Diff Hygiene

```bash
git diff --check -- ecm-frontend/src/services/shareLinkService.ts \
  ecm-frontend/src/services/shareLinkService.test.ts \
  docs/SHARE_LINK_SERVICE_SHAPE_GUARDS_DESIGN_VERIFICATION_20260514.md
```

Result: passed.

### Remote CI

Run: pending.

Commit: pending.

Result: pending.

## Residual Work

- This does not add new share-link product capability.
- Deactivate and delete endpoints still trust HTTP success/failure rather than
  response-body shape because they are designed as no-content endpoints.
- `ShareLinkManager` and `AdminDashboard` component tests are unchanged in this
  slice; this slice covers the service contract only.
- Other frontend services may still need similar shape guards; this slice only
  covers share-link service reads and readbacks used by current consumers.
