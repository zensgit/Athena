# Service Guards: Bulk Metadata + Blog Integration Verification

Date: 2026-05-15

## Scope

This round continued the frontend service response-shape guard closeout line.
It hardened two small services that previously trusted typed API responses
directly:

- `bulkMetadataService`
- `blogService`

Both slices preserve their existing public APIs, endpoint paths, request
payloads, and return types.

## Parallel Development Split

Codex implemented and verified the bulk-metadata slice in the main worktree:

- `ecm-frontend/src/services/bulkMetadataService.ts`
- `ecm-frontend/src/services/bulkMetadataService.test.ts`
- `docs/BULK_METADATA_SERVICE_SHAPE_GUARDS_DESIGN_VERIFICATION_20260515.md`

Claude implemented the blog slice in an isolated worktree:

- Worktree: `.claude/worktrees/claude-blog-service-guards`
- Branch: `worktree-claude-blog-service-guards`
- Worktree commit: `dff9d0c fix(blog): guard service responses`
- Integrated commit: `0868f5f fix(blog): guard service responses`

Claude wrote the implementation, tests, and design document. Codex stopped the
agent after it produced files but no final command output, reviewed the diff,
temporarily reused the main worktree's `node_modules` for verification, removed
that symlink before staging, committed the worktree, and cherry-picked it back
to `main`.

## Backend Contract Checks

Bulk metadata:

- Backend controller: `BulkOperationController`
- Mount: `/api/v1/bulk`
- Frontend relative path remains `POST /bulk/metadata`
- Response shape: `BulkMetadataResult`

Blog:

- Backend controller: `BlogController`
- Mounts: `/api/sites/{siteId}/blog` and `/api/v1/sites/{siteId}/blog`
- Frontend relative paths remain under `/sites/{siteId}/blog/posts`
- Page responses remain Spring page envelopes with `content`,
  `totalElements`, `totalPages`, `number`, and `size`
- Delete remains `204 No Content`

## Guard Rules Added

Bulk metadata:

- Rejects HTML fallback and malformed result objects.
- Validates required operation string and numeric count fields.
- Validates `successfulIds` as `string[]`.
- Validates `failures` as `Record<string, string>`.

Blog:

- Rejects HTML fallback and malformed page/post responses.
- Validates `BlogStatus` closed union: `DRAFT | PUBLISHED`.
- Validates required post strings, nullable `content` and `publishedDate`,
  `tags: string[]`, and Spring page envelope fields.
- Leaves delete unguarded because the backend returns no response body.

## Local Verification

Targeted service tests after integration:

```bash
cd ecm-frontend
npm test -- --runTestsByPath src/services/bulkMetadataService.test.ts src/services/blogService.test.ts --watchAll=false
```

Result: PASS. 2 test suites, 28 tests, 0 failures.

Frontend lint:

```bash
cd ecm-frontend
npm run lint
```

Result: PASS.

Frontend production build:

```bash
cd ecm-frontend
CI=true npm run build
```

Result: PASS. The build emitted the existing Node `fs.F_OK` deprecation
warning and CRA bundle-size advisory.

Remote CI after push:

```bash
gh run watch 25902851085 --exit-status --interval 30
```

Result: PASS. Run `25902851085` completed green for all seven jobs:

- Backend Verify
- Frontend Build & Test
- Phase C Security Verification
- Frontend E2E Core Gate
- Property Encryption Closeout Gate
- Phase 5 Mocked Regression Gate
- Acceptance Smoke (3 admin pages)

## Commits

- `0d6226c fix(bulk-metadata): guard service responses`
- `0868f5f fix(blog): guard service responses`
- `c474b55 docs(services): record bulk metadata blog guard verification`

## Notes

The main worktree still has the pre-existing local `.env` modification. It was
not staged or changed by this round.
