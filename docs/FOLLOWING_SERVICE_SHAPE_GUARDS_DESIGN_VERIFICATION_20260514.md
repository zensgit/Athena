# Following Service Shape Guards Design and Verification

## Context

The current frontend hardening line is closing service boundaries that can
mistake SPA HTML fallback or malformed JSON for valid API data. `followingService`
is a small but shared surface:

- `FileList` loads follow subscriptions for visible nodes and toggles node
  follows.
- `SitesPage` lists subscriptions, checks site follow state, and toggles site
  follows.
- `PeopleDirectoryPage` checks and toggles user follow state.
- `ActivityFeedPage` lists subscriptions, groups them by target type, and
  unfollows targets.

Some utility tests cover grouping/link behavior, but those tests construct
`FollowSubscriptionDto` objects directly. A malformed `/followings`,
`/followings/check`, or `POST /followings` response could still pass mocked
coverage while breaking runtime state. This slice makes the service reject
malformed response bodies before components group, link, or render follow
subscriptions.

## Design

- Add a shared `FOLLOWING_UNEXPECTED_RESPONSE_MESSAGE` for malformed following
  responses.
- Guard `list(...)` with a structural `FollowSubscriptionDto[]` validator:
  - required `id`, `userId`, `targetId`, and `createdAt` strings;
  - required `targetType` enum value: `USER`, `SITE`, or `NODE`.
- Guard `check(...)` with a strict boolean validator.
- Guard `follow(...)` with the same `FollowSubscriptionDto` validator used by
  list responses.
- Keep `unfollow(...)` unchanged because it is a no-content delete endpoint and
  current consumers only await HTTP success/failure.
- Keep validators structural rather than target-id-format exhaustive. The
  backend normalizes and validates target IDs differently for users, sites, and
  nodes.

## Files Changed

- `ecm-frontend/src/services/followingService.ts`
- `ecm-frontend/src/services/followingService.test.ts`

## Verification

### Targeted Service Test

```bash
cd ecm-frontend
CI=true npm test -- --runTestsByPath src/services/followingService.test.ts --watchAll=false
```

Result:

- 1 suite passed
- 8 tests passed
- New coverage rejects HTML fallback for subscription lists; rejects malformed
  subscription list items, malformed boolean checks, and malformed follow
  readbacks; accepts guarded list, check, follow, and unfollow endpoint wiring.

### Existing Utility Consumer Test

```bash
cd ecm-frontend
CI=true npm test -- --runTestsByPath src/services/followingService.test.ts \
  src/utils/followingUtils.test.ts --watchAll=false
```

Result:

- 2 suites passed
- 11 tests passed
- Existing follow grouping/link utility tests still pass with the guarded
  service contract.

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
git diff --check -- ecm-frontend/src/services/followingService.ts \
  ecm-frontend/src/services/followingService.test.ts \
  docs/FOLLOWING_SERVICE_SHAPE_GUARDS_DESIGN_VERIFICATION_20260514.md
```

Result: passed.

### Remote CI

Pending. This section will be updated after the commit is pushed and the main
branch CI run completes.

## Residual Work

- This does not add new following product capability.
- `unfollow(...)` still trusts HTTP success/failure rather than response-body
  shape because it is designed as a no-content endpoint.
- Following UI flows still lack dedicated component tests in this checkout.
- Other frontend services may still need similar shape guards; this slice only
  covers following service reads and readbacks used by current following
  consumers.
