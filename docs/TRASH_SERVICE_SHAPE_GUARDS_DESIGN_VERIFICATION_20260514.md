# Trash Service Shape Guards Design and Verification

## Context

The frontend hardening line continues to close service boundaries that can
mistake SPA HTML fallback or malformed JSON for valid API data.
`trashService` is the only consumer of `TrashController`, which backs
`TrashPage` recycle-bin listing, restore, permanent delete, and empty
operations. The previous service untyped every response as the expected DTO
via `api.get<TrashItem[]>(...)` and `api.delete<{ deletedCount: number }>(...)`,
so an HTML fallback or backend shape drift would crash a renderer at the
first field read instead of failing fast at the service boundary.

`TrashController.TrashItemResponse` returns a plain JSON array. `nodeType`
is the `Node.NodeType` enum name (`FOLDER` or `DOCUMENT` only) and
`isFolder` is a primitive boolean. `size`, `deletedBy`, `deletedAt`,
`createdBy`, and `createdDate` are nullable on the entity and therefore
nullable on the wire. `DELETE /trash/empty` returns
`Map.of("deletedCount", int)`. This slice guards malformed responses
without rejecting the valid nullable backend states.

## Design

- Add a shared `TRASH_UNEXPECTED_RESPONSE_MESSAGE` for malformed trash
  responses.
- Structural validators:
  - `TrashItem`: string `id`, `name`, `path`; `nodeType` restricted to
    `FOLDER | DOCUMENT`; nullable-number `size`; nullable-string
    `deletedBy`, `deletedAt`, `createdBy`, `createdDate`; boolean
    `isFolder`.
  - `TrashItem[]` (list): must be an array of valid `TrashItem`.
  - `EmptyTrashResponse`: numeric `deletedCount`.
- Guard `getTrashItems` and `emptyTrash` at the service boundary.
- Keep `restore` and `permanentDelete` unchanged. They are no-content
  endpoints; current consumers only await HTTP success/failure.
- Preserve the existing endpoint paths and request shapes verbatim — this
  slice is response-shape hardening, not a wiring change.

## Files Changed

- `ecm-frontend/src/services/trashService.ts`
- `ecm-frontend/src/services/trashService.test.ts`
- `docs/TRASH_SERVICE_SHAPE_GUARDS_DESIGN_VERIFICATION_20260514.md`

## Verification

### Targeted Service Tests

```bash
cd ecm-frontend
CI=true npm test -- --runTestsByPath src/services/trashService.test.ts \
  --watchAll=false
```

Result:

- 1 suite passed
- 7 tests passed
- New coverage rejects HTML fallback for trash lists; rejects malformed trash
  items and malformed `deletedCount`; preserves restore and permanent-delete
  endpoint wiring.

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
git diff --check -- ecm-frontend/src/services/trashService.ts \
  ecm-frontend/src/services/trashService.test.ts \
  docs/TRASH_SERVICE_SHAPE_GUARDS_DESIGN_VERIFICATION_20260514.md
```

Result: passed.

### Remote CI

Run: `25851208310`

Commit: `f9cc1d4 fix(trash): guard service responses`

Result: passed.

- Backend Verify: passed
- Frontend Build & Test: passed
- Phase C Security Verification: passed
- Property Encryption Closeout Gate: passed
- Frontend E2E Core Gate: passed
- Acceptance Smoke (3 admin pages): passed
- Phase 5 Mocked Regression Gate: passed

## Residual Work

- This does not add new trash product capability.
- `restore` and `permanentDelete` still trust HTTP success/failure rather
  than response-body shape because they are designed as no-content
  endpoints.
- `TrashPage` component tests are unchanged in this slice; this slice
  covers the service contract only.
- Admin-only endpoints `getTrashItemsForUser`, `getTrashStats`, and
  `getItemsNearingPurge` are not currently wired through `trashService` —
  they are out of scope for this slice and would be guarded if/when a
  frontend consumer is added.
- Other frontend services may still need similar shape guards; this slice
  only covers trash service reads and readbacks used by current consumers.
