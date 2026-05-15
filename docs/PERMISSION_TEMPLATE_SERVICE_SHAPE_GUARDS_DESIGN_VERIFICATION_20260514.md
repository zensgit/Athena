# Permission Template Service Shape Guards Design and Verification

## Context

The frontend hardening line continues to close service boundaries that can
mistake SPA HTML fallback or malformed JSON for valid API data.
`permissionTemplateService` backs `PermissionTemplatesPage` (admin CRUD,
version history, rollback, diff export) and the template picker on
`PermissionsDialog` (list + apply). Before this slice, all reads and
readbacks trusted the API body shape directly: list/version endpoints
returned `PermissionTemplate[]`/`PermissionTemplateVersion[]` against an
untyped axios body, and mutation/rollback/version-detail readbacks were
typed as the expected DTO without any structural check.

The backend wire contract was grep-verified against
`PermissionTemplateController` `@RequestMapping` paths and the
corresponding entity and DTO classes:

- `PermissionTemplateController` mounts at
  `/api/v1/security/permission-templates`. All paths below are relative
  to the frontend `api` base of `/api/v1`.
- `GET /security/permission-templates` returns
  `List<PermissionTemplate>` (entity).
- `POST /security/permission-templates` returns `200 OK` with a
  `PermissionTemplate` body (ADMIN gated).
- `PUT /security/permission-templates/{id}` returns `200 OK` with a
  `PermissionTemplate` body (ADMIN gated).
- `DELETE /security/permission-templates/{id}` returns `200 OK` with no
  body (`ResponseEntity.ok().build()`).
- `POST /security/permission-templates/{id}/apply?nodeId={uuid}&replace={bool}`
  returns `200 OK` with no body.
- `GET /security/permission-templates/{id}/versions` returns
  `List<PermissionTemplateVersionDto>`.
- `GET /security/permission-templates/{id}/versions/{versionId}` returns
  a `PermissionTemplateVersionDetailDto` body.
- `POST /security/permission-templates/{id}/versions/{versionId}/rollback`
  returns a `PermissionTemplate` body.
- `GET /security/permission-templates/{id}/versions/diff/export?from&to&format`
  returns a CSV or JSON attachment (`Content-Disposition: attachment`).

Backend DTO/entity shapes:

- `PermissionTemplate` (entity, extends `BaseEntity`): required `id`
  (UUID, serialized as string), `name`, plus `entries` (always
  initialized to `new ArrayList<>()`), `createdBy`, and `createdDate`
  from `BaseEntity` (audited columns); nullable `description`. The
  `BaseEntity.createdBy`/`createdDate` columns are DB-non-null but the
  frontend type leaves them optional because legacy seeds and some
  testing paths may not populate them when serialized. The guard accepts
  missing audit fields but rejects explicit `null` for `createdDate`
  because `PermissionTemplatesPage.formatDate` expects `string |
  undefined`.
- `PermissionTemplate.PermissionTemplateEntry`: `authority` (string),
  `authorityType` (`USER`/`GROUP`/`ROLE`/`EVERYONE` from
  `Permission.AuthorityType`), `permissionSet` (`PermissionSet` enum:
  `COORDINATOR`/`EDITOR`/`CONTRIBUTOR`/`CONSUMER`, serialized as the
  string name).
- `PermissionTemplateVersionDto`: required `id`, `templateId` (UUID
  strings), `versionNumber` (Integer, DB-non-null), `name`,
  `entryCount` (int, primitive); nullable `description`, `createdBy`,
  `createdDate`.
- `PermissionTemplateVersionDetailDto`: same as above plus required
  `entries` (mapped from the entity list).

This slice rejects malformed responses without rejecting the valid
nullable backend states above.

## Design

- Add a shared `PERMISSION_TEMPLATE_UNEXPECTED_RESPONSE_MESSAGE` for
  malformed permission-template responses.
- Structural validators:
  - `PermissionTemplateEntry`: string `authority`; `authorityType`
    constrained to the backend `Permission.AuthorityType` literals
    (`USER`/`GROUP`/`ROLE`/`EVERYONE`); string `permissionSet`. The
    frontend keeps `permissionSet` as a free string so the UI stays
    forward-compatible with future server-side permission set names
    without a coordinated type change.
  - `PermissionTemplate`: string `id`, `name`; nullable-string
    `description`; array of valid `PermissionTemplateEntry` for
    `entries`; optional-string `createdBy` and `createdDate`.
  - `PermissionTemplateVersion`: string `id`, `templateId`; numeric
    `versionNumber` and `entryCount`; string `name`; nullable-string
    `description`; optional-string `createdBy` and `createdDate`.
  - `PermissionTemplateVersionDetail`: same as `PermissionTemplate`
    plus numeric `versionNumber` and string `templateId`.
  - Array validators reject non-array values and any element that
    fails the per-record check.
- Guard `list`, `create`, `update`, `listVersions`, `rollbackVersion`,
  and `getVersionDetail` with the validators.
- Leave `remove`, `apply`, and `exportVersionDiff` unchanged. The first
  two are no-content endpoints; current consumers only await HTTP
  success/failure. `exportVersionDiff` is a blob attachment endpoint
  used to trigger a browser download — there is no JSON shape to guard.
- Preserve the existing endpoint paths, query params, and request
  payloads verbatim (grep-verified against `PermissionTemplateController`
  `@RequestMapping` paths) — this slice is response-shape hardening,
  not a wiring change.

## Files Changed

- `ecm-frontend/src/services/permissionTemplateService.ts`
- `ecm-frontend/src/services/permissionTemplateService.test.ts`
- `docs/PERMISSION_TEMPLATE_SERVICE_SHAPE_GUARDS_DESIGN_VERIFICATION_20260514.md`

## Verification

### Targeted Service Test

```bash
cd ecm-frontend
CI=true npm test -- --runTestsByPath src/services/permissionTemplateService.test.ts \
  --watchAll=false
```

Result:

- 1 suite passed.
- 23 tests passed.
- Coverage includes `list` (success with nullable detail fields including
  backend-valid `ROLE`/`EVERYONE` authority types, HTML fallback rejection,
  malformed array entry with unsupported `OWNER` authorityType,
  non-array body rejection), `create` (success with payload forwarding,
  malformed readback, HTML fallback), `update` (success with payload
  forwarding, malformed readback), `remove` (no-content wiring), `apply`
  (no-content wiring with explicit `replace=true`, default `replace=false`),
  `listVersions` (success with nullable detail fields, HTML fallback,
  malformed array entry with non-numeric `entryCount`), `rollbackVersion`
  (success, malformed readback), `getVersionDetail` (success with full
  and nullable detail fields, malformed `versionNumber`, HTML fallback),
  and `exportVersionDiff` (CSV blob forwarding, JSON blob forwarding).

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

Build caught one integration issue during this verification pass:
`PermissionTemplatesPage.formatDate` expects `string | undefined`, so the
service type and guard must not return explicit `null` for audit timestamps.
The final guard accepts omitted `createdBy`/`createdDate` but rejects
explicit `null`; nullable descriptions remain accepted.

### Diff Hygiene

```bash
git diff --check -- ecm-frontend/src/services/permissionTemplateService.ts \
  ecm-frontend/src/services/permissionTemplateService.test.ts \
  docs/PERMISSION_TEMPLATE_SERVICE_SHAPE_GUARDS_DESIGN_VERIFICATION_20260514.md
```

Result: passed.

### Mainline Integration

```bash
cd ecm-frontend
CI=true npm test -- --runTestsByPath src/services/templateService.test.ts \
  src/services/permissionTemplateService.test.ts --watchAll=false
```

Result:

- 2 suites passed.
- 36 tests passed.

Mainline `npm run lint`, `CI=true npm run build`, and `git diff --check
HEAD~2..HEAD` also passed after cherry-picking this worktree commit onto the
`templateService` slice.

### Remote CI

Pending after push.

## Residual Work

- This does not add new permission-template product capability.
- `remove` and `apply` still trust HTTP success/failure rather than
  response-body shape because they are designed as no-content
  endpoints.
- `exportVersionDiff` still trusts HTTP success/failure rather than
  response-body shape because it is a blob attachment endpoint used to
  trigger a browser download.
- The current `PermissionTemplatesPage` authoring UI still only emits
  `USER`/`GROUP` entries. The service type and guard intentionally accept
  backend-valid `ROLE`/`EVERYONE` entries so imported or API-created
  templates do not fail at the service boundary.
- `PermissionTemplatesPage` and `PermissionsDialog` component tests are
  unchanged in this slice; this slice covers the service contract only.
- Other frontend services may still need similar shape guards; this
  slice only covers permission-template service reads and readbacks
  used by current consumers.
