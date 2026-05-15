# Localized Content Service Shape Guards Design and Verification

## Context

The frontend hardening line continues to close service boundaries that can
mistake SPA HTML fallback or malformed JSON for valid API data.
`localizedContentService` backs `LocalizedContentPage`, which lists,
upserts, deletes, and resolves per-locale title/description overrides for
a node. Before this slice, every read and readback trusted the API body
shape directly: `listLocalizations` returned `LocalizedContentDto[]`
against an untyped axios body, and `upsertLocalization` and
`resolveLocalization` were typed as `LocalizedContentDto` without any
structural check.

The backend wire contract was grep-verified against
`LocalizedContentController` `@RequestMapping` paths and the
corresponding `LocalizedContentService` DTO record:

- `LocalizedContentController` mounts at
  `/api/v1/nodes/{nodeId}`. All paths below are relative to the frontend
  `api` base of `/api/v1`.
- `GET /nodes/{nodeId}/localizations` returns
  `List<LocalizedContentDto>`.
- `PUT /nodes/{nodeId}/localizations/{locale}` returns `200 OK` with a
  `LocalizedContentDto` body; request body is
  `LocalizedContentRequest`. The path `locale` is authoritative; the
  request body `locale` field is ignored.
- `DELETE /nodes/{nodeId}/localizations/{locale}` returns `204 No
  Content` with no body.
- `GET /nodes/{nodeId}/localization` returns a `LocalizedContentDto`
  body resolved against the `Accept-Language` header, or `404 Not
  Found` if no localizations exist for the node.

Backend DTO shape (`LocalizedContentService.LocalizedContentDto`
record):

- `id` (UUID, serialized as string) — required.
- `nodeId` (UUID, serialized as string) — required in practice. The
  service `toDto` defends against a null `node` but the controller
  flows always pass through `requireReadableNode`/`requireWritableNode`
  which loads a live `Node` before any DTO is produced, so the
  serialized value is always a populated UUID string for the responses
  the frontend reads.
- `locale` (String) — required; the entity column is `NOT NULL` and
  the service normalizes to lowercase before persist.
- `title` (String) — nullable; the entity column is nullable.
- `description` (String) — nullable; the entity column is nullable.
- `createdDate` (LocalDateTime, serialized as ISO string) — required
  via `BaseEntity.@CreatedDate` (DB `NOT NULL`).
- `createdBy` (String) — required via `BaseEntity.@CreatedBy` (DB
  `NOT NULL`).
- `lastModifiedDate` (LocalDateTime, serialized as ISO string) —
  nullable; `BaseEntity.@LastModifiedDate` is unset on first persist
  for some Spring Data audit configurations.

This slice rejects malformed responses without rejecting the valid
nullable backend states above.

## Design

- Add a shared `LOCALIZED_CONTENT_UNEXPECTED_RESPONSE_MESSAGE` for
  malformed localized-content responses.
- Structural validator `isLocalizedContentDto` enforces:
  - string `id`, `nodeId`, `locale`, `createdDate`, `createdBy`.
  - nullable-string `title`, `description`, `lastModifiedDate`
    (accepts `null`; rejects `undefined` and non-string).
  - object body (rejects strings — catches SPA `index.html`
    fallback — and arrays).
- Array validator rejects non-array values and any element that fails
  the per-record check.
- Guard `listLocalizations`, `upsertLocalization`, and
  `resolveLocalization` with the validators.
- Leave `deleteLocalization` unchanged. It is a no-content endpoint
  (`204 No Content`); current consumers only await HTTP success/
  failure and have no body to validate.
- Preserve the existing endpoint paths verbatim — including the
  `encodeURIComponent(locale)` behavior on the upsert/delete path
  segments and the `Accept-Language` header behavior on resolve —
  grep-verified against `LocalizedContentController` `@RequestMapping`
  paths and `LocalizedContentService.parseAcceptLanguage`. This slice
  is response-shape hardening, not a wiring change.

## Files Changed

- `ecm-frontend/src/services/localizedContentService.ts`
- `ecm-frontend/src/services/localizedContentService.test.ts`
- `docs/LOCALIZED_CONTENT_SERVICE_SHAPE_GUARDS_DESIGN_VERIFICATION_20260514.md`

## Verification

### Targeted Service Test

```bash
cd ecm-frontend
CI=true npm test -- --runTestsByPath src/services/localizedContentService.test.ts \
  --watchAll=false
```

Result:

- 1 suite passed.
- 14 tests passed.

Test coverage (encoded in `localizedContentService.test.ts`):

- `listLocalizations`: success with nullable detail fields (`title`,
  `description`, `lastModifiedDate` all `null`); HTML fallback
  rejection (string body — SPA `index.html`); non-array object body
  rejection; malformed array entry rejection (non-string `locale`).
- `upsertLocalization`: success with readback guard, payload
  forwarding, and `encodeURIComponent` on a plain BCP-47 locale
  (`zh-CN` round-trips unchanged) and on a locale with a space
  character (`zh CN` -> `zh%20CN`); malformed readback rejection;
  HTML fallback readback rejection.
- `deleteLocalization`: no-content wiring with
  `encodeURIComponent(locale)` on the path segment.
- `resolveLocalization`: success with readback guard and joined
  `navigator.languages` as the `Accept-Language` header
  (`['zh-CN','zh','en']` -> `zh-CN,zh,en`); fallback to
  `navigator.language` when `navigator.languages` is empty; default
  `'en'` when both are empty; malformed readback rejection; HTML
  fallback readback rejection.

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
advisory, and Node emits the known `fs.F_OK` dependency deprecation
warning; neither failed the build.

### Diff Hygiene

```bash
git diff --check -- ecm-frontend/src/services/localizedContentService.ts \
  ecm-frontend/src/services/localizedContentService.test.ts \
  docs/LOCALIZED_CONTENT_SERVICE_SHAPE_GUARDS_DESIGN_VERIFICATION_20260514.md
```

Result: passed (no whitespace errors).

### Remote CI

Pending after mainline integration and push.

## Residual Work

- This does not add new localized-content product capability.
- `deleteLocalization` still trusts HTTP success/failure rather than
  response-body shape because it is designed as a `204 No Content`
  endpoint.
- `LocalizedContentPage` component tests are unchanged in this slice;
  this slice covers the service contract only.
- Other frontend services may still need similar shape guards; this
  slice only covers localized-content service reads and readbacks used
  by current consumers.
