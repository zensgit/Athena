# Favorite Service Shape Guards Design and Verification

## Context

The current frontend hardening line is closing service boundaries that can
mistake SPA HTML fallback or malformed JSON for valid API data. `favoriteService`
is a small but shared surface:

- `FileList` batch-checks favorite IDs for visible nodes and toggles add/remove
  actions from list, grid, and context-menu paths.
- `FavoritesPage` loads the favorite page and then hydrates each favorited node
  through `nodeService`.

Those consumers do not have a dedicated service-contract test in this checkout.
Malformed `/favorites`, `/favorites/{nodeId}/check`, or
`/favorites/batch/check` responses could therefore pass mocked UI coverage while
breaking runtime state. This slice makes the service reject malformed response
bodies before components read favorite pages or build favorite ID sets.

## Design

- Add a shared `FAVORITE_UNEXPECTED_RESPONSE_MESSAGE` for malformed favorite
  responses.
- Guard `check(...)` with a strict boolean validator.
- Guard `list(...)` with the Spring page fields used by the frontend:
  - required `content` array of favorite items;
  - required numeric `totalElements`, `totalPages`, `size`, and `number`;
  - required favorite item `id`, `nodeId`, `nodeName`, `nodeType`, and
    `createdAt`;
  - `nodeType` must be `FOLDER` or `DOCUMENT`, matching backend
    `NodeType.name()`.
- Guard `checkBatch(...)` with `{ favoritedNodeIds: string[] }` before building
  the returned `Set<string>`.
- Preserve the existing empty-batch fast path so no API request is made for
  `checkBatch([])`.
- Keep `add(...)` and `remove(...)` unchanged because they are no-content
  mutation endpoints and current consumers only await HTTP success/failure.

## Files Changed

- `ecm-frontend/src/services/favoriteService.ts`
- `ecm-frontend/src/services/favoriteService.test.ts`

## Verification

### Targeted Service Test

```bash
cd ecm-frontend
CI=true npm test -- --runTestsByPath src/services/favoriteService.test.ts --watchAll=false
```

Result:

- 1 suite passed
- 9 tests passed
- New coverage rejects malformed favorite check responses, HTML fallback for
  favorite pages, malformed favorite page items, and malformed batch-check
  responses; accepts guarded boolean checks, favorite pages, batch-check `Set`
  conversion, empty-batch short-circuiting, and add/remove endpoint wiring.

### Consumer Test Availability

No existing `favoriteService`, `FavoritesPage`, or dedicated favorite `FileList`
test file is present in this checkout. Consumer compatibility for this slice is
therefore covered by TypeScript, lint, and production build rather than a
targeted component regression suite.

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
git diff --check -- ecm-frontend/src/services/favoriteService.ts \
  ecm-frontend/src/services/favoriteService.test.ts \
  docs/FAVORITE_SERVICE_SHAPE_GUARDS_DESIGN_VERIFICATION_20260514.md
```

Result: passed.

### Remote CI

Pending. This section will be updated after the commit is pushed and the main
branch CI run completes.

## Residual Work

- This does not add new favorite product capability.
- `add(...)` and `remove(...)` still trust HTTP success/failure rather than
  response-body shape because they are designed as no-content endpoints.
- Favorite UI flows still lack dedicated component tests in this checkout.
- Other frontend services may still need similar shape guards; this slice only
  covers favorite service reads and readbacks used by current favorite
  consumers.
