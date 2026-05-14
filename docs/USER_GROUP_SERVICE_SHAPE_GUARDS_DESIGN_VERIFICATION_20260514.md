# User/Group Service Shape Guards Design and Verification

## Context

The frontend hardening line continues to close service boundaries that can
mistake SPA HTML fallback or malformed JSON for valid API data.
`userGroupService` backs the user admin and group admin flows for searching
users, listing users, creating and updating users, listing groups, creating
groups, deleting groups, and group membership add/remove. Before this slice,
all reads and readbacks trusted the API body shape directly: list endpoints
returned `res.content || []` against an untyped `PageResponse<T>`, and
mutation readbacks were typed as the expected DTO without any structural
check.

The backend wire contract is:

- `UserController` returns `Page<UserDto>` from `GET /users` and a
  `UserDto` body from `POST /users` and `PUT /users/{username}`. `UserDto`
  has required `id`, `username`, `email`, and `roles` (always emitted as an
  array — `List.of()` on Keycloak role-fetch failure); nullable
  `firstName`, `lastName`, `enabled`, and `locked` because the wrappers can
  be null in some backends.
- `GroupController` returns `Page<GroupDto>` from `GET /groups`, a
  `GroupDto` body from `POST /groups`, and an empty `204 No Content` from
  `DELETE /groups/{name}`. `POST /groups/{groupName}/members/{username}`
  and `DELETE /groups/{groupName}/members/{username}` are no-content
  endpoints. `GroupDto` has required `name`; nullable `id`, `displayName`,
  `description`, `email`, `enabled`, `groupType`, and `users` (`null` from
  the `LocalUserGroupBackend.toDto` and `KeycloakUserGroupBackend.toDto`
  group converters).

This slice rejects malformed responses without rejecting the valid nullable
backend states above.

## Design

- Add a shared `USER_GROUP_UNEXPECTED_RESPONSE_MESSAGE` for malformed
  user/group responses.
- Structural validators:
  - `User`: string `id`, `username`, `email`; `roles` as `string[]`;
    nullable-string `firstName` and `lastName`; nullable-boolean `enabled`
    and `locked`.
  - `Group`: string `name`; nullable-string `id`, `displayName`,
    `description`, `email`, and `groupType`; nullable-boolean `enabled`;
    nullable `users` array of valid `User` values.
  - `UserPage` / `GroupPage`: Spring `Page<T>` envelope with valid
    `content` array, plus numeric `totalElements`, `totalPages`, `number`,
    and `size`.
- Guard `searchUsers` and `listUsers` via the user-page validator,
  returning `content`.
- Guard `listGroups` via the group-page validator, returning `content`.
- Guard `createUser`, `updateUser`, and `createGroup` readbacks with the
  per-record validators.
- Keep `deleteGroup`, `addUserToGroup`, and `removeUserFromGroup`
  unchanged. They are no-content endpoints; current consumers only await
  HTTP success/failure.
- Preserve the existing endpoint paths, query params, and request payloads
  verbatim — this slice is response-shape hardening, not a wiring change.

## Files Changed

- `ecm-frontend/src/services/userGroupService.ts`
- `ecm-frontend/src/services/userGroupService.test.ts`
- `docs/USER_GROUP_SERVICE_SHAPE_GUARDS_DESIGN_VERIFICATION_20260514.md`

## Verification

### Targeted Service Test

```bash
cd ecm-frontend
CI=true npm test -- --runTestsByPath src/services/userGroupService.test.ts \
  --watchAll=false
```

Result:

- 1 suite passed
- 16 tests passed
- New coverage rejects HTML fallback for user and group pages; rejects malformed
  user/group page items and mutation readbacks; accepts nullable backend detail
  fields; preserves delete and membership endpoint wiring.

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
git diff --check -- ecm-frontend/src/services/userGroupService.ts \
  ecm-frontend/src/services/userGroupService.test.ts \
  docs/USER_GROUP_SERVICE_SHAPE_GUARDS_DESIGN_VERIFICATION_20260514.md
```

Result: passed.

### Remote CI

Run: pending.

Commit: pending.

Result: pending.

## Residual Work

- This does not add new user/group product capability.
- `deleteGroup`, `addUserToGroup`, and `removeUserFromGroup` still trust
  HTTP success/failure rather than response-body shape because they are
  designed as no-content endpoints.
- The frontend `User` and `Group` types declare some fields as required
  even though the wire allows nullable values. This slice mirrors the
  frontend type contract (required string `id`, `username`, `email` on
  `User`; required string `name` on `Group`) and treats the genuinely
  nullable wire fields permissively. Tightening or relaxing the frontend
  type contract is out of scope here.
- `UserManagementPage` and `GroupManagementPage` component tests are
  unchanged in this slice; this slice covers the service contract only.
- Other frontend services may still need similar shape guards; this slice
  only covers user/group service reads and readbacks used by current
  consumers.
