# Service Guards: Activity + Correspondent Integration Verification

Date: 2026-05-14

## Scope

This round continued the frontend service response-shape guard closeout line.
It hardened two services that still trusted mocked API responses directly:

- `activityService`
- `correspondentService`

Both slices preserve their existing public APIs, endpoint paths, request params,
and return types.

## Parallel Development Split

Codex implemented and verified the activity slice in the main worktree:

- `ecm-frontend/src/services/activityService.ts`
- `ecm-frontend/src/services/activityService.test.ts`
- `docs/ACTIVITY_SERVICE_SHAPE_GUARDS_DESIGN_VERIFICATION_20260514.md`

Claude implemented the correspondent slice in an isolated worktree:

- Worktree: `.claude/worktrees/claude-correspondent-service-guards`
- Branch: `worktree-claude-correspondent-service-guards`
- Integrated commit: `97f32a2`

Codex reviewed Claude's diff against the backend contract, reran verification
because Claude's session could not run `npm` or `git` commands, updated the
verification doc with real results, committed the worktree, and cherry-picked it
back to `main`.

## Backend Contract Checks

Activity:

- Backend controller: `ActivityController`
- Mounts: `/api/activities` and `/api/v1/activities`
- Frontend relative path remains `/activities`
- Response shape: Spring `Page<ActivityDto>`

Correspondent:

- Backend controller: `CorrespondentController`
- Mount: `/api/v1/correspondents`
- Frontend relative path remains `/correspondents`
- `list` response shape: Spring `Page<Correspondent>`
- `create` and `update` response shape: single `Correspondent`

## Guard Rules Added

Activity:

- Rejects HTML fallback and non-page envelopes.
- Validates `content` array items.
- Validates numeric page metadata: `totalElements`, `totalPages`, `number`, `size`.
- Requires `id`, `activityType`, `userId`, `postedAt` as strings.
- Accepts nullable or omitted `siteId`, `nodeId`, `nodeName`.
- Requires `summary` to be a plain object.

Correspondent:

- Rejects HTML fallback, bare arrays, malformed page envelopes, and malformed readbacks.
- Validates `matchAlgorithm` against `AUTO`, `ANY`, `ALL`, `EXACT`, `REGEX`, `FUZZY`.
- Requires `id`, `name`, `matchAlgorithm`, and boolean `insensitive`.
- Accepts nullable or omitted `matchPattern`, `email`, `phone`, and audit strings.

## Verification

Targeted service tests after integration:

```bash
cd ecm-frontend
npm test -- --runTestsByPath src/services/activityService.test.ts src/services/correspondentService.test.ts --watchAll=false
```

Result: PASS. 2 test suites, 22 tests, 0 failures.

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

Result: PASS. CRA emitted the existing bundle-size advisory.

## Commits

- `9dfb676 fix(activities): guard service responses`
- `97f32a2 fix(correspondents): guard service responses`

## Notes

The Claude split was useful for parallel development, but Codex still had to
own cross-boundary review and verification. Claude's harness could write files
but could not run `npm` or `git add`/`git commit`, so Codex reran the gates and
performed the final integration.

The main worktree still has the pre-existing local `.env` modification. It was
not staged or changed by this round.
