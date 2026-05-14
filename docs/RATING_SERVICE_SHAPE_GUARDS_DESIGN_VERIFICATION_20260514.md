# Rating Service Shape Guards Design and Verification

## Context

The current frontend hardening line is closing service boundaries that can
mistake SPA HTML fallback or malformed JSON for valid API data. `ratingService`
is a small but visible surface:

- `RatingBadge` loads summary/current-user ratings and toggles likes in list and
  grid contexts.
- `NodeRatingPanel` loads the same summary/current-user ratings and supports
  both like and five-star updates.

`RatingBadge` has component tests, but those tests mock `ratingService` directly.
That means an HTML fallback or malformed `/nodes/{id}/ratings/*` response can
still pass mocked component tests while breaking runtime rendering. This slice
makes the service reject malformed response bodies before the components read
summary counts, averages, or current-user scores.

## Design

- Add a shared `RATING_UNEXPECTED_RESPONSE_MESSAGE` for malformed rating
  responses.
- Guard `listRatings(...)` with a structural `RatingDto[]` validator:
  - required `id`, `userId`, and `createdAt` strings;
  - required `scheme` enum value: `LIKES` or `FIVE_STAR`;
  - required finite numeric `score`.
- Guard `rate(...)` with the same `RatingDto` readback validator.
- Guard `getSummary(...)` with the backend `RatingSummaryResponse` shape:
  - required `likes` and `fivestar` objects;
  - required finite numeric `count`, `average`, and `total` on each scheme
    summary.
- Guard `getMyRatings(...)` with nullable numeric `likeScore` and `starScore`,
  matching the backend `Integer` response fields.
- Keep `removeRating(...)` unchanged because it is a no-content delete endpoint
  and current consumers only await HTTP success/failure.
- Keep validators structural rather than range-exhaustive. Score ranges and
  business validation remain backend-owned.

## Files Changed

- `ecm-frontend/src/services/ratingService.ts`
- `ecm-frontend/src/services/ratingService.test.ts`

## Verification

### Targeted Service Test

```bash
cd ecm-frontend
CI=true npm test -- --runTestsByPath src/services/ratingService.test.ts --watchAll=false
```

Result:

- 1 suite passed
- 10 tests passed
- New coverage rejects HTML fallback for rating lists; rejects malformed rating
  list items, mutation readbacks, summary responses, and current-user rating
  responses; accepts guarded list, mutation readback, summary, current-user
  ratings, and delete endpoint wiring.

### Existing Component Test

```bash
cd ecm-frontend
CI=true npm test -- --runTestsByPath src/services/ratingService.test.ts \
  src/components/ratings/RatingBadge.test.tsx --watchAll=false
```

Result:

- 2 suites passed
- 18 tests passed
- Existing `RatingBadge` mocked-service consumer tests still pass with the new
  service contract guards.

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
git diff --check -- ecm-frontend/src/services/ratingService.ts \
  ecm-frontend/src/services/ratingService.test.ts \
  docs/RATING_SERVICE_SHAPE_GUARDS_DESIGN_VERIFICATION_20260514.md
```

Result: passed.

### Remote CI

Run: `25840284003`

Commit: `1a9d627 fix(ratings): guard service responses`

Result: passed.

- Backend Verify: passed
- Frontend Build & Test: passed
- Phase C Security Verification: passed
- Property Encryption Closeout Gate: passed
- Frontend E2E Core Gate: passed
- Acceptance Smoke (3 admin pages): passed
- Phase 5 Mocked Regression Gate: passed

## Residual Work

- This does not add new rating product capability.
- `removeRating(...)` still trusts HTTP success/failure rather than
  response-body shape because it is designed as a no-content endpoint.
- `NodeRatingPanel` still has no dedicated component test in this checkout.
  Service contract coverage plus lint/build covers this slice.
- Other frontend services may still need similar shape guards; this slice only
  covers rating service reads and readbacks used by current rating consumers.
