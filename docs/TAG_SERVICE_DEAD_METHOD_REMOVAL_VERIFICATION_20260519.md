# Tag Service Dead Method Removal: Verification

Date: 2026-05-19

## Scope

The frontend service response-shape guard backlog listed
`tagService.findNodesByTag` / `tagService.findNodesByTags` (the only two
untyped `any` / `any[]` methods in `tagService`) as the next minimal
candidate.

Orientation found both methods are **dead code in the frontend**:

- `grep -rn "findNodesByTag" ecm-frontend/src` (recursive, all files)
  returns only `tagService.ts` itself — zero page, component, or test
  consumers.
- `tagService.test.ts` does not exercise either method.
- Neither method uses any of the existing `tagService` guard helpers
  (`TAG_UNEXPECTED_RESPONSE_MESSAGE`, `isTag`, `assertTagList`, etc.);
  they were raw `api.get` / `api.post` calls.

Because the guard track's value is preventing a real consumer from
crashing on an HTML fallback / malformed body, guarding unreachable
methods reduces no current risk. Per the gate ruling, the true minimal
and cleanest deliverable was to **remove** the two dead methods,
eliminating the `any` surface entirely. The gate confirmed there is no
external or planned consumer.

This round did not change backend code, contracts, endpoint paths,
request payloads, query params, other services, pages, e2e tests,
migrations, or `.env`. `.env` was neither staged nor modified.

The parent records/mail guard round is already integrated and green in
CI (`origin/main` includes `5f4323f` … `7b0f658`), so the worktree
isolation gate no longer applies; this trivial removal was done directly
on `main` with the standard closeout, mirroring the records/mail
closeout itself.

## Files Touched

- `ecm-frontend/src/services/tagService.ts` — removed
  `findNodesByTag(tagName, page, size): Promise<any>` and
  `findNodesByTags(tagNames): Promise<any[]>` (10 deletions, no other
  change).
- `docs/TAG_SERVICE_DEAD_METHOD_REMOVAL_VERIFICATION_20260519.md` (this
  file).

No helper became unused: the removed methods used no guard helpers, and
every remaining guarded method (`createTag`, `getAllTags`, `searchTags`,
`getPopularTags`, `updateTag`, `getTagCloud`, `getNodeTags`) still uses
the existing bundle unchanged.

## Verification

Targeted Jest:

```bash
cd ecm-frontend
CI=true npm test -- --runTestsByPath src/services/tagService.test.ts --watchAll=false
```

Result:

```text
Test Suites: 1 passed, 1 total
Tests:       12 passed, 12 total
```

(The existing suite is unaffected — it never referenced the removed
methods.)

Frontend lint:

```bash
cd ecm-frontend
npm run lint
```

Result: PASS. Removing the two methods also removed the file's only two
`any` usages.

Frontend production build:

```bash
cd ecm-frontend
CI=true npm run build
```

Result: PASS.

Diff hygiene:

```bash
git diff --check -- . ':!.env'
```

Result: PASS.

## CI Follow-Up

Done directly on `main` (no cherry-pick; the records/mail integration
gate is satisfied).

Pushed CI run:

- Run: `26082652038`
- Head: `44f1e0d`
- Result: PASS

Passing jobs:

- `Backend Verify`
- `Frontend Build & Test`
- `Phase C Security Verification`
- `Phase 5 Mocked Regression Gate`
- `Frontend E2E Core Gate`
- `Property Encryption Closeout Gate`
- `Acceptance Smoke (3 admin pages)`

The CI-sensitive checks matched local expectations:

- `Frontend Build & Test` covered lint, type check, build, and the
  tagService unit suite (12 tests, unaffected by the removal).
- `Phase 5 Mocked Regression Gate`, `Frontend E2E Core Gate`, and
  `Acceptance Smoke (3 admin pages)` stayed green — the removed methods
  had no consumers, so no surface changed.
- Backend/security/property-encryption gates stayed green because this
  round did not change backend code or migrations.

## Follow-Up

- Remaining service-guard backlog: `nodeService`, to be split into
  thematic sub-slices (folder/node CRUD, lock/checkout, search/preview,
  batch-download, relations/renditions, version/history, permissions) —
  one sub-slice at a time, no big-bang change.
