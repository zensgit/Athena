# Comment Service Shape Guards Design and Verification

## Context

The current frontend hardening line is closing service boundaries that can
mistake SPA HTML fallback or malformed JSON for valid API data.
`commentService` is a high-risk remaining surface because it is used by:

- `CommentSection` for node comment trees, search, add/edit/delete, and
  reactions.
- `PeopleDirectoryPage` for authored and mentioned comment previews.

Before this slice, `getNodeComments(...)` returned `Promise<any>` and every
comment read/readback method trusted the API body shape. A missing route or HTML
fallback could therefore flow into comment rendering, recursive tree expansion,
or People Directory profile state without a service-boundary failure.

## Design

- Add a shared `COMMENT_UNEXPECTED_RESPONSE_MESSAGE` for malformed comment
  responses.
- Guard `Comment` readbacks and recursive tree/search arrays:
  - required `id`, `content`, `author`, and `created` strings;
  - nullable/optional backend metadata fields: `nodeId`, `nodeName`, `nodeType`,
    `edited`, and `editor`;
  - required numeric `level`;
  - required string `mentionedUsers`;
  - required reaction objects with string `type`, `user`, and `date`;
  - optional recursive `replies` array.
- Change `getNodeComments(...)` from `Promise<any>` to
  `Promise<PageResponse<Comment>>`.
- Guard all paginated comment responses with a structural page validator.
- Guard `CommentStatistics` with a strict numeric `topCommenters` map.
- Keep delete/reaction endpoints unchanged because they are designed as
  no-content endpoints and current consumers only await HTTP success/failure.
- Align `CommentSection`'s local comment type with the backend DTO by accepting
  nullable `edited` and `editor` values.
- Keep `PeopleDirectoryPage` quick-action calls compatible with nullable
  comment `nodeType` metadata.

## Files Changed

- `ecm-frontend/src/services/commentService.ts`
- `ecm-frontend/src/services/commentService.test.ts`
- `ecm-frontend/src/components/comments/CommentSection.tsx`
- `ecm-frontend/src/pages/PeopleDirectoryPage.tsx`

## Verification

### Targeted Service Test

```bash
cd ecm-frontend
CI=true npm test -- --runTestsByPath src/services/commentService.test.ts --watchAll=false
```

Result:

- 1 suite passed
- 14 tests passed
- New coverage rejects HTML fallback for comment pages; rejects malformed
  comment page items, recursive trees, search results, readbacks, and comment
  statistics; preserves delete/reaction endpoint wiring.

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
git diff --check -- ecm-frontend/src/services/commentService.ts \
  ecm-frontend/src/services/commentService.test.ts \
  ecm-frontend/src/components/comments/CommentSection.tsx \
  ecm-frontend/src/pages/PeopleDirectoryPage.tsx \
  docs/COMMENT_SERVICE_SHAPE_GUARDS_DESIGN_VERIFICATION_20260514.md
```

Result: passed.

### Remote CI

Run: pending.

Commit: pending.

Result: pending.

## Residual Work

- This does not add new comment product capability.
- Delete and reaction endpoints still trust HTTP success/failure rather than
  response-body shape because they are no-content endpoints.
- `CommentSection` still defines its own local comment type; this slice only
  aligns nullable edit metadata instead of refactoring the component to import
  the service type.
- Other frontend services may still need similar shape guards; this slice only
  covers comment service reads and readbacks used by current consumers.
