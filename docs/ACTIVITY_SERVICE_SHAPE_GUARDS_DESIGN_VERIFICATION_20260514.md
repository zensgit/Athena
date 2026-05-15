# Activity Service Shape Guards - Design and Verification

Date: 2026-05-14

## Context

The frontend service-guard closeout line is removing a repeated mocked-CI blind spot: service calls can receive the SPA HTML fallback or another malformed payload while frontend tests still pass because the API module is mocked.

This slice hardens `activityService` without changing its public API.

## Backend Contract Evidence

`ActivityController` is mounted at both `/api/activities` and `/api/v1/activities`. The frontend API base defaults to `/api/v1`, so the existing relative `/activities` routes remain correct.

The controller returns `Page<ActivityDto>` for:

- `GET /activities`
- `GET /activities/users/{userId}`
- `GET /activities/sites/{siteId}`
- `GET /activities/following`
- `GET /activities/nodes/{nodeId}`

`ActivityDto` fields from the backend record are:

- Required strings in the frontend contract: `id`, `activityType`, `userId`, `postedAt`
- Nullable or omitted strings in the frontend contract: `siteId`, `nodeId`, `nodeName`
- Required object map: `summary`

The Spring `Page` fields consumed by the frontend are `content`, `totalElements`, `totalPages`, `number`, and `size`.

## Design

`ecm-frontend/src/services/activityService.ts` now:

- Exports `ACTIVITY_UNEXPECTED_RESPONSE_MESSAGE` as the stable user-safe error.
- Calls `api.get<unknown>` and validates the returned value before exposing it as `ActivityPage`.
- Rejects HTML fallback, non-page envelopes, malformed page metadata, and malformed activity items.
- Preserves all existing method names, return types, endpoint paths, and paging params.

Guard rules:

- `content` must be an array and every item must match `ActivityDto`.
- `totalElements`, `totalPages`, `number`, and `size` must be finite numbers.
- `summary` must be a plain object, not `null` or an array.
- Nullable backend fields accept `string`, `null`, or `undefined`.

## Files Changed

- `ecm-frontend/src/services/activityService.ts`
- `ecm-frontend/src/services/activityService.test.ts`

## Verification

Targeted frontend verification:

```bash
cd ecm-frontend
npm test -- --runTestsByPath src/services/activityService.test.ts --watchAll=false
```

Result will be recorded after integration verification.

Result: PASS. `activityService.test.ts` ran 9 tests, 0 failures.

Full frontend gates:

```bash
cd ecm-frontend
npm run lint
CI=true npm run build
```

Result will be recorded after integration verification.

Result: PASS. `npm run lint` completed cleanly. `CI=true npm run build` completed cleanly with the existing CRA bundle-size advisory.

## Residual Risk

This is a client-side response-shape guard only. It does not change backend authorization, backend pagination, or activity creation behavior.
