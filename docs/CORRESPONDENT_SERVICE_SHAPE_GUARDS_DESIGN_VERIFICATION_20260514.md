# Correspondent Service Shape Guards Design and Verification

## Context

The frontend service hardening line continues to close API boundaries where
the SPA HTML fallback or malformed JSON can be treated as successful DTO data.
`correspondentService` backs the correspondent admin surface and any document
forms that read or mutate correspondents. Before this slice, `list`, `create`,
and `update` trusted the response body shape directly.

The backend contract comes from `CorrespondentController` and `Correspondent`:

- `CorrespondentController` is mounted at `/api/v1/correspondents`.
- `GET /correspondents` returns `Page<Correspondent>` from Spring Data, i.e. an
  envelope of `{ content, totalElements, totalPages, number, size, ... }`.
- `POST /correspondents` and `PUT /correspondents/{id}` return one
  `Correspondent`.
- `Correspondent` fields: required `id` (UUID, serialized as string) and `name`
  (non-null); optional `matchPattern`, `email`, `phone` (DB-nullable);
  `matchAlgorithm` defaults to `"AUTO"` and accepts `AUTO`, `ANY`, `ALL`,
  `EXACT`, `REGEX`, `FUZZY`; `insensitive` is a primitive boolean defaulting to
  `true`. Audit fields inherited from `BaseEntity` (`createdDate`, `createdBy`,
  `lastModifiedDate`, `lastModifiedBy`) are JSON strings when populated and may
  be omitted/null for newly built entities.

## Design

- Add exported `CORRESPONDENT_UNEXPECTED_RESPONSE_MESSAGE`.
- Constrain `MatchAlgorithm` checks at runtime against the six-value union
  (`AUTO`, `ANY`, `ALL`, `EXACT`, `REGEX`, `FUZZY`).
- Guard `Correspondent` with:
  - required string `id` and `name`;
  - required `matchAlgorithm` constrained to the union;
  - required boolean `insensitive`;
  - nullable string `matchPattern`, `email`, `phone`;
  - nullable string audit fields `createdDate`, `createdBy`,
    `lastModifiedDate`, `lastModifiedBy`.
- Guard `CorrespondentPage` as an object whose `content` is an array of valid
  correspondents and whose `totalElements`, `totalPages`, `number`, `size` are
  finite numbers. Reject any value that is not such an envelope, including
  HTML strings, bare arrays, or empty objects.
- Guard `list`, `create`, and `update`. Preserve the public API (`list()` still
  returns `Correspondent[]`; `create`/`update` still return `Correspondent`) and
  preserve the existing endpoint paths and request params/payloads.

## Files Changed

- `ecm-frontend/src/services/correspondentService.ts`
- `ecm-frontend/src/services/correspondentService.test.ts`
- `docs/CORRESPONDENT_SERVICE_SHAPE_GUARDS_DESIGN_VERIFICATION_20260514.md`

## Guard Rules Summary

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `id` | string | yes | UUID, serialized as string |
| `name` | string | yes | Unique in DB |
| `matchAlgorithm` | `'AUTO'\|'ANY'\|'ALL'\|'EXACT'\|'REGEX'\|'FUZZY'` | yes | Closed union check |
| `insensitive` | boolean | yes | Primitive `boolean`, defaults true server-side |
| `matchPattern` | string \| null | no | TEXT column, nullable |
| `email` | string \| null | no | Nullable |
| `phone` | string \| null | no | Nullable |
| `createdDate` | string \| null | no | Audit, may be absent on transient entities |
| `createdBy` | string \| null | no | Audit |
| `lastModifiedDate` | string \| null | no | Audit |
| `lastModifiedBy` | string \| null | no | Audit |

For `list`, the page envelope itself must be a non-array object with a
`content` array and finite numeric `totalElements`, `totalPages`, `number`,
`size`. Anything else (HTML fallback, bare array, malformed envelope, or
malformed item) throws `Error(CORRESPONDENT_UNEXPECTED_RESPONSE_MESSAGE)`.

## Verification

### Targeted Service Test

Command:

```bash
cd ecm-frontend
CI=true npm test -- --runTestsByPath src/services/correspondentService.test.ts --watchAll=false
```

Result: **PASS**. Re-run by Codex after Claude produced the files. The Claude
worktree had no local `node_modules`, so verification temporarily reused the
main worktree dependency cache through a symlink that was removed before
staging. `correspondentService.test.ts` ran 13 tests, 0 failures.

Test coverage in `correspondentService.test.ts`:

- `list` success with default and custom page params (verifies forwarded
  `{ page, size, sort: 'name,asc' }`).
- `list` accepts items with nullable optional fields.
- `list` rejects HTML fallback.
- `list` rejects a response that is a bare array (no page envelope).
- `list` rejects a page with a malformed `name` content item.
- `list` rejects unsupported `matchAlgorithm` values.
- `list` rejects non-boolean `insensitive`.
- `list` rejects non-string-or-null audit fields.
- `create` success with payload forwarding; rejects malformed readback (null
  `id`).
- `update` success with payload forwarding; rejects malformed readback
  (invalid `matchAlgorithm`).

All assertions are observable (return values, mocked call arguments, thrown
error messages). No DOM access.

### Full Frontend Gates

Commands:

```bash
cd ecm-frontend
npm run lint
CI=true npm run build
```

Result: **PASS**. `npm run lint` completed cleanly. `CI=true npm run build`
completed cleanly with the existing CRA bundle-size advisory.

### Remote CI

Not yet triggered; this slice is committed in the worktree only and not
pushed, per the task scope.

## Residual Work

- This slice does not add new correspondent product capability.
- Other frontend services may still need equivalent response-shape guards
  (the broader hardening line is ongoing).
