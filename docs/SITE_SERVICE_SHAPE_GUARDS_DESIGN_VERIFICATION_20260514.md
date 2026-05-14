# Site Service Shape Guards Design and Verification

## Context

The frontend hardening line continues to close service boundaries that can
mistake SPA HTML fallback or malformed JSON for valid API data.
`siteService` backs the collaboration sites flows on `SitesPage` (and is
imported by `siteInvitationService` for the shared `SiteMemberRole` type).
It owns: site CRUD, membership-request listing/creation/approval/rejection/
withdrawal, and the per-site members roster (list/add/update-role/remove).

Before this slice, all reads and readbacks trusted the API body shape
directly: list endpoints returned `SiteDto[]`/`MembershipRequestDto[]`/
`SiteMemberDto[]` against an untyped axios body, and mutation readbacks
were typed as the expected DTO without any structural check.

The backend wire contract is:

- `SiteController` mounts at `/api/v1/sites`. All paths below are relative
  to the frontend `api` base of `/api/v1`.
- `GET /sites?includeArchived={bool}` returns `List<SiteService.SiteDto>`.
- `GET /sites/{siteId}` returns a single `SiteDto`.
- `POST /sites` returns `201 Created` with a `SiteDto` body (ADMIN gated).
- `PUT /sites/{siteId}` returns `200 OK` with a `SiteDto` body (ADMIN gated).
- `DELETE /sites/{siteId}` returns `204 No Content`.
- `GET /sites/{siteId}/membership-requests` returns
  `List<MembershipRequestDto>`.
- `POST /sites/{siteId}/membership-requests` returns `201 Created` with a
  `MembershipRequestDto` body.
- `POST /sites/{siteId}/membership-requests/{username}/approve` returns
  `200 OK` with a `MembershipRequestDto` body. Optional `{comment}` body.
- `POST /sites/{siteId}/membership-requests/{username}/reject` returns
  `200 OK` with a `MembershipRequestDto` body. Optional `{comment}` body.
- `DELETE /sites/{siteId}/membership-requests` returns `204 No Content`.
- `GET /sites/{siteId}/members` returns `List<SiteMemberDto>`.
- `POST /sites/{siteId}/members` returns `201 Created` with a
  `SiteMemberDto` body. Payload is `{username, role}`.
- `PUT /sites/{siteId}/members/{username}` returns `200 OK` with a
  `SiteMemberDto` body. Payload is `{role}`.
- `DELETE /sites/{siteId}/members/{username}` returns `204 No Content`.

Backend DTO shapes (from `SiteService` and `SiteMembershipService`):

- `SiteDto`: required `id` (UUID, serialized as string), `siteId`, `title`,
  `visibility` (`PUBLIC`/`MODERATED`/`PRIVATE`), `status`
  (`ACTIVE`/`ARCHIVED`), `createdBy`, `createdDate` (LocalDateTime,
  serialized as ISO string), and `deleted` (boolean). Nullable
  `description`, `rootFolderId`, `rootFolderTitle`, `rootFolderPath`, and
  `lastModifiedDate`, plus nullable deletion metadata `deletedAt` and
  `deletedBy`.
- `MembershipRequestDto`: required `username`, `siteId`, `role`, and
  `status`. Nullable `siteTitle`, `message`, `requestedAt`, `decisionBy`,
  `decisionAt`, and `decisionComment` (all stringified or omitted by the
  backend `toDto` mappers when the source is null).
- `SiteMemberDto`: required `id`, `siteId`, `username`, and `role`
  (`MANAGER`/`COLLABORATOR`/`CONTRIBUTOR`/`CONSUMER`). Nullable `joinedAt`
  (the backend toMemberDto guards against a null `joinedAt`).

This slice rejects malformed responses without rejecting the valid
nullable backend states above.

## Design

- Add a shared `SITE_UNEXPECTED_RESPONSE_MESSAGE` for malformed site
  responses.
- Structural validators:
  - `SiteDto`: string `id`, `siteId`, `title`, `createdBy`, `createdDate`;
    `visibility` constrained to the `SiteVisibility` enum
    (`PUBLIC`/`MODERATED`/`PRIVATE`); `status` constrained to the
    `SiteStatus` enum (`ACTIVE`/`ARCHIVED`); boolean `deleted`;
    nullable-string `description`, `rootFolderId`, `rootFolderTitle`,
    `rootFolderPath`, `lastModifiedDate`, `deletedAt`, and `deletedBy`.
  - `MembershipRequestDto`: string `username`, `siteId`, `role`, `status`;
    nullable-string `siteTitle`, `message`, `requestedAt`, `decisionBy`,
    `decisionAt`, and `decisionComment`. `role` and `status` are accepted
    as any non-empty string so the frontend stays forward-compatible with
    new server-side role names or status values it has not yet been
    taught.
  - `SiteMemberDto`: string `id`, `siteId`, `username`; `role` constrained
    to `MANAGER`/`COLLABORATOR`/`CONTRIBUTOR`/`CONSUMER`; nullable-string
    `joinedAt`.
  - Array validators reject non-array values and any element that fails
    the per-record check.
- Guard `listSites`, `getSite`, `createSite`, and `updateSite` with the
  site validators.
- Guard `getMembershipRequests`, `requestMembership`,
  `approveMembershipRequest`, and `rejectMembershipRequest` with the
  membership-request validators.
- Guard `getMembers`, `addMember`, and `updateMemberRole` with the
  member validators.
- Leave `deleteSite`, `withdrawMembershipRequest`, and `removeMember`
  unchanged. They are no-content endpoints; current consumers only await
  HTTP success/failure.
- Preserve the existing endpoint paths, query params, and request payloads
  verbatim (grep-verified against `SiteController` `@RequestMapping`
  paths) — this slice is response-shape hardening, not a wiring change.

## Files Changed

- `ecm-frontend/src/services/siteService.ts`
- `ecm-frontend/src/services/siteService.test.ts`
- `docs/SITE_SERVICE_SHAPE_GUARDS_DESIGN_VERIFICATION_20260514.md`

## Verification

### Targeted Service Test

```bash
cd ecm-frontend
CI=true npm test -- --runTestsByPath src/services/siteService.test.ts \
  --watchAll=false
```

Result:

- 1 suite passed.
- 32 tests passed.
- Coverage includes: list/get/create/update site happy paths with
  nullable detail fields; HTML fallback rejection on list endpoints;
  malformed array entry rejection on list endpoints; mutation readback
  rejection for site/membership-request/member shapes; no-content wiring
  for `deleteSite`, `withdrawMembershipRequest`, and `removeMember`;
  payload forwarding for create/approve/reject/add/update-role calls;
  default `CONSUMER` role on `addMember`.

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
git diff --check -- ecm-frontend/src/services/siteService.ts \
  ecm-frontend/src/services/siteService.test.ts \
  docs/SITE_SERVICE_SHAPE_GUARDS_DESIGN_VERIFICATION_20260514.md
```

Result: passed.

### Mainline Integration

```bash
cd ecm-frontend
CI=true npm test -- --runTestsByPath src/services/siteInvitationService.test.ts \
  src/services/siteService.test.ts --watchAll=false
```

Result:

- 2 suites passed.
- 41 tests passed.

Mainline `npm run lint`, `CI=true npm run build`, and `git diff --check
HEAD~2..HEAD` also passed after cherry-picking this worktree commit onto the
`siteInvitationService` slice.

### Remote CI

Run: pending (record after the worktree branch is pushed and CI completes).

Commit: `7fd6894 fix(sites): guard service responses`.

Expected gates: Backend Verify, Frontend Build & Test, Phase C Security
Verification, Property Encryption Closeout Gate, Frontend E2E Core Gate,
Acceptance Smoke (3 admin pages), Phase 5 Mocked Regression Gate.

## Residual Work

- This does not add new site product capability.
- `deleteSite`, `withdrawMembershipRequest`, and `removeMember` still
  trust HTTP success/failure rather than response-body shape because they
  are designed as no-content endpoints.
- The frontend `MembershipRequestDto.role` guard accepts any string rather
  than the `SiteMemberRole` enum literals, so site request rows stay
  forward-compatible with future server-side request role values.
- `SitesPage` and `SiteInvitationsPage` component tests are unchanged in
  this slice; this slice covers the service contract only.
- Other frontend services may still need similar shape guards; this slice
  only covers site service reads and readbacks used by current consumers.
