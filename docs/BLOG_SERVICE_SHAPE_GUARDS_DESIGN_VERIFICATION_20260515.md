# Blog Service Shape Guards Design and Verification

## Context

The frontend service hardening line continues to close API boundaries where
the SPA HTML fallback or a malformed JSON payload can be treated as a valid
DTO. `blogService` is the frontend consumer of the site blog API
(`/sites/{siteId}/blog/posts`, `/sites/{siteId}/blog/posts/drafts`,
`/sites/{siteId}/blog/posts/{postId}`, `/publish`, `/unpublish`). Before
this slice every method trusted the response body shape and forwarded it
directly to the typed return value, so an `index.html` fallback (HTTP 200
with a string body) or a backend regression that dropped a field would
surface as a downstream type error rather than a clear "unexpected
response" failure.

## Backend Contract Evidence

`ecm-core/src/main/java/com/ecm/core/controller/BlogController.java`:

- Mounted at `/api/sites/{siteId}/blog` and `/api/v1/sites/{siteId}/blog`
  (the frontend `api.ts` base path adds the `/api/v1` prefix, so frontend
  relative paths remain `/sites/{siteId}/blog/posts`,
  `/sites/{siteId}/blog/posts/drafts`,
  `/sites/{siteId}/blog/posts/{postId}`,
  `/sites/{siteId}/blog/posts/{postId}/publish`,
  `/sites/{siteId}/blog/posts/{postId}/unpublish`).
- `GET /sites/{siteId}/blog/posts` (pageable, optional `status` query) →
  `ResponseEntity<Page<BlogPostDto>>`.
- `GET /sites/{siteId}/blog/posts/drafts` (pageable) →
  `ResponseEntity<Page<BlogPostDto>>`.
- `GET /sites/{siteId}/blog/posts/{postId}` →
  `ResponseEntity<BlogPostDto>`.
- `POST /sites/{siteId}/blog/posts` →
  `ResponseEntity<BlogPostDto>` (HTTP 201). Body: `CreateBlogPostRequest`
  (`title`, `content`, `tags`).
- `PUT /sites/{siteId}/blog/posts/{postId}` →
  `ResponseEntity<BlogPostDto>`. Body: `UpdateBlogPostRequest`
  (`title`, `content`, `tags`).
- `POST /sites/{siteId}/blog/posts/{postId}/publish` →
  `ResponseEntity<BlogPostDto>`.
- `POST /sites/{siteId}/blog/posts/{postId}/unpublish` →
  `ResponseEntity<BlogPostDto>`.
- `DELETE /sites/{siteId}/blog/posts/{postId}` → `ResponseEntity<Void>`
  (HTTP 204, no body).

`BlogController.BlogPostDto` record:

```
BlogPostDto(UUID id, String siteId, String title, String content,
            BlogStatus status, LocalDateTime publishedDate,
            List<String> tags, String createdBy, LocalDateTime createdDate)
```

`ecm-core/src/main/java/com/ecm/core/entity/BlogPost.java` confirms which
columns are `nullable = false` and which are nullable on the wire:

- Non-null on the wire: `id` (UUID PK), `siteId` (`nullable = false`),
  `title` (`nullable = false`), `status` (`nullable = false`,
  enum-serialized as string), `tags` (defaulted to `new ArrayList<>()`,
  serialized as a JSON array — never `null`), `createdBy` (audited,
  `nullable = false`), `createdDate` (audited, `nullable = false`).
- Nullable on the wire: `content` (no `nullable = false`),
  `publishedDate` (only populated once the post is published).

`BlogStatus` is the enum `DRAFT | PUBLISHED` and serializes as its string
name. The frontend mirrors this union verbatim in `blogService.ts`.

For `listPosts` and `listDrafts`, Spring serializes a `Page<BlogPostDto>`
so the JSON envelope always carries `content`, `totalElements`,
`totalPages`, `number`, and `size`. The guard only enforces these five
fields — other Spring page fields (`first`, `last`, `sort`, `pageable`,
`empty`) are ignored so backend metadata changes do not break the
boundary.

## Design

- Export `BLOG_UNEXPECTED_RESPONSE_MESSAGE` — a stable, user-safe phrasing
  aligned with sibling guarded services (`BULK_IMPORT_*`, `SCRIPT_*`,
  etc.).
- Preserve the public API surface: same eight methods, same return types,
  same endpoint paths, same `create`/`update` payload shape (`{ title,
  content, tags }`), same `publish`/`unpublish` POST-with-no-body shape,
  same paging parameter shape (`{ params: { page, size } }`), same
  optional `status` query forwarding in `listPosts`.
- Validate every response with a structural guard at the boundary,
  throwing `Error(BLOG_UNEXPECTED_RESPONSE_MESSAGE)` on any mismatch.
- Convert each `api.get` / `api.post` / `api.put` call to `<unknown>` and
  assert the shape before returning to the caller. `deletePost` stays a
  no-content `api.delete` call and resolves to `void`.

## Files Changed

- `ecm-frontend/src/services/blogService.ts`
- `ecm-frontend/src/services/blogService.test.ts`
- `docs/BLOG_SERVICE_SHAPE_GUARDS_DESIGN_VERIFICATION_20260515.md`

## Guard Rules

### `BlogPostDto`

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `id` | string | yes | UUID, serialized as string |
| `siteId` | string | yes | Non-null column |
| `title` | string | yes | Non-null column |
| `status` | `BlogStatus` union | yes | `DRAFT \| PUBLISHED` |
| `tags` | `string[]` | yes | Backend defaults to empty list, never null |
| `createdBy` | string | yes | Audited, non-null |
| `createdDate` | string | yes | ISO-8601 timestamp |
| `content` | string \| null \| undefined | no | Nullable text column |
| `publishedDate` | string \| null \| undefined | no | Only set on publish |

### `BlogPostPage`

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `content` | `BlogPostDto[]` | yes | Every entry guarded |
| `totalElements` | finite number | yes | |
| `totalPages` | finite number | yes | |
| `number` | finite number | yes | |
| `size` | finite number | yes | |

Anything that fails any of the above (HTML fallback string, non-array
`content`, missing required field, wrong-typed nullable field, unsupported
`status` value, stringified numeric paging field, tags array with a
non-string entry, tags serialized as a string) throws
`Error(BLOG_UNEXPECTED_RESPONSE_MESSAGE)`.

`deletePost` is a no-content endpoint (`HTTP 204`) and has no shape to
guard — it resolves to `void` after the `api.delete` call settles.

## Test Coverage

`ecm-frontend/src/services/blogService.test.ts` mocks `./api` (`get`,
`post`, `put`, `delete`) and asserts only observable behavior — return
values, mocked call arguments (URL, paging params, optional `status`
filter, create/update body, publish/unpublish path), and thrown error
messages. No DOM or network access is performed.

- `listPosts`:
  - Default paging (`page=0`, `size=20`) forwards
    `/sites/{siteId}/blog/posts` with `{ params: { page, size } }` and
    returns a guarded page.
  - Explicit `status` filter forwards the third query param verbatim.
  - HTML fallback rejected.
  - Page entry with `status` outside the closed union rejected (invalid
    status enum).
  - Page entry with non-string `tags` value rejected (malformed tags).
  - Page envelope with stringified `totalPages` rejected (malformed page
    item — page-level field mismatch).
- `listDrafts`:
  - Explicit `(1, 10)` forwards
    `/sites/{siteId}/blog/posts/drafts` with the paging params.
  - Default `(siteId)` forwards `{ page: 0, size: 20 }` (default paging).
  - HTML fallback rejected.
- `getPost`:
  - Success path forwards `/sites/{siteId}/blog/posts/{postId}` and
    returns the guarded post.
  - Nullable `content` and `publishedDate` accepted via the `draftPost`
    fixture (covers nullable-fields-accepted requirement).
  - HTML fallback rejected.
  - Non-string `createdBy` rejected.
- `createPost`:
  - Forwards `{ title, content, tags }` to POST without rewriting (covers
    "keep create payloads unchanged").
  - HTML fallback rejected.
- `updatePost`:
  - Partial body (just `{ title }`) is forwarded verbatim to PUT (covers
    "keep update payloads unchanged").
  - Response whose `tags` is a string (instead of `string[]`) rejected
    (malformed tags on update path).
- `publish` / `unpublish`:
  - `publish` forwards POST to
    `/sites/{siteId}/blog/posts/{postId}/publish` with no body and
    returns the guarded post (covers "keep publish payloads unchanged").
  - `unpublish` forwards POST to
    `/sites/{siteId}/blog/posts/{postId}/unpublish` with no body and
    returns the guarded post (covers "keep unpublish payloads
    unchanged").
  - HTML fallback rejected on publish.
  - Status outside the closed union rejected on unpublish.
- `deletePost`:
  - Forwards DELETE `/sites/{siteId}/blog/posts/{postId}` and resolves
    to `void` (covers no-content response).

## Verification

### Targeted Service Test

Intended command:

```bash
cd ecm-frontend
CI=true npm test -- --runTestsByPath src/services/blogService.test.ts --watchAll=false
```

Result: **Not run locally.** The Claude worktree has no local
`node_modules` and the operator did not grant permission to install a
fresh tree or reuse the main worktree's cache via symlink, so the
targeted test could not be executed inside this worktree. The Codex-side
gate (or a subsequent verification pass with `node_modules` available)
is expected to run this.

Codex follow-up verification:

```bash
cd ecm-frontend
npm test -- --runTestsByPath src/services/blogService.test.ts --watchAll=false
```

Result: PASS. 1 test suite, 22 tests, 0 failures.

### Full Frontend Gates

Intended commands:

```bash
cd ecm-frontend
npm run lint
CI=true npm run build
```

Result: **Not run locally**, same reason as above (no `node_modules`
in the worktree).

Codex follow-up verification:

```bash
cd ecm-frontend
npm run lint
CI=true npm run build
```

Result: PASS. `CI=true npm run build` emitted the existing Node `fs.F_OK`
deprecation warning and CRA bundle-size advisory.

### Remote CI

Not triggered. This slice is committed in the worktree branch only and
not pushed, per the task scope.

## Residual Work

- This slice does not add new blog product capability.
- Other frontend services may still need equivalent response-shape
  guards (the broader hardening line is ongoing).
- Local lint, type, and Jest verification should run before this slice
  is merged.
