# People Service Shape Guards Design and Verification

Date: 2026-05-18

## Scope

- Added runtime response-shape guards for `ecm-frontend/src/services/peopleService.ts` JSON-returning methods.
- Preserved existing endpoint paths, request params, request bodies, username/site/node encoding, and void delete behavior.
- Covered people search/get, groups, favorites, preferences, activities, sites, favorite sites, site membership requests, profile updates, and preference updates.
- Left `.env`, package files, backend files, UI pages, and preview diagnostics files untouched.

## Design

- Exported `PEOPLE_UNEXPECTED_RESPONSE_MESSAGE` so tests and callers have one stable failure message for malformed people API responses.
- Changed JSON-returning `api.get/post/put/delete<T>` calls to `api.*<unknown>` and validate the unknown value before returning typed DTOs.
- Validated page envelopes with `content`, `totalElements`, `totalPages`, `number`, and `size`; malformed counts and nested item arrays are rejected.
- Validated required fields for current DTO usage while keeping optional fields permissive enough for backend evolution.
- Kept preference values JSON-compatible and unconstrained, but required preference containers and export/import envelopes to be objects rather than arrays or HTML fallback strings.
- Kept delete methods that are typed as `Promise<void>` response-agnostic and unchanged except for awaiting the same endpoint call.

## Coverage

- Success-path tests assert guarded returns and unchanged API calls for:
  - People search, user get, groups.
  - Favorites page/get/create/delete.
  - Preference get/namespaces/export/import/get/set/delete/clear.
  - Activities, sites, favorite sites, user-visible membership requests, visible membership request page.
  - Favorite-site get/create/delete.
  - Membership create/update/approve/reject/withdraw.
  - `updateProfile` and `updatePreferences`.
- Negative-path tests assert rejection for:
  - HTML fallback payloads.
  - Malformed page counts.
  - Nested arrays inside page/list content.
  - Malformed preference object/list envelopes.

## Verification Results

Commands run from `/Users/chouhua/Downloads/Github/Athena` unless noted:

```bash
cd ecm-frontend && npm test -- --runTestsByPath src/services/peopleService.test.ts --watchAll=false
```

Result: passed. `Test Suites: 1 passed, 1 total`; `Tests: 11 passed, 11 total`.

```bash
cd ecm-frontend && npm run lint
```

Result: passed. `eslint src --ext .ts,.tsx` completed with exit code 0.

```bash
git diff --check -- ecm-frontend/src/services/peopleService.ts ecm-frontend/src/services/peopleService.test.ts docs/PEOPLE_SERVICE_SHAPE_GUARDS_DESIGN_VERIFICATION_20260518.md
```

Result: passed. No whitespace errors reported.

```bash
cd ecm-frontend && CI=true npm run build
```

Result: passed. Production build compiled successfully. The build emitted the existing Create React App bundle-size recommendation and a Node `fs.F_OK` deprecation warning.
