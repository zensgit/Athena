# SecurityController Permission Response-Contract Tests

Date: 2026-05-22

## Context

This slice follows the NodeController response-contract slices. The initial TODO
called out "NodeController permissions", but discovery showed the actual backend
entry point is `SecurityController` under `/api/v1/security`, while the frontend
`nodeService` calls `/security/nodes/...`.

The goal is to lock the permission read contracts consumed by
`PermissionsDialog` and `PermissionTemplatesPage`.

## Scope

Added:

- `ecm-core/src/test/java/com/ecm/core/controller/SecurityControllerPermissionResponseContractTest.java`

Covered endpoints:

- `GET /api/v1/security/nodes/{nodeId}/permissions`
- `GET /api/v1/security/nodes/{nodeId}/permission-diagnostics`
- `GET /api/v1/security/permission-sets`
- `GET /api/v1/security/permission-sets/metadata`

Out of scope:

- Permission write endpoints.
- PermissionTemplateController version/diff/export endpoints.
- User/current-authority endpoints.
- Controller implementation changes.
- Frontend guard changes.

## Design

The test uses standalone `MockMvc` with mocked `SecurityController`
dependencies and a Jackson `ObjectMapper` configured with `JavaTimeModule` plus
`WRITE_DATES_AS_TIMESTAMPS` disabled.

The slice locks these wire DTO field sets:

### `PermissionDto`

- `id`
- `authority`
- `authorityType`
- `permission`
- `allowed`
- `inherited`
- `expiryDate`
- `notes`

The test locks explicit nulls for `expiryDate` and `notes` to guard against
accidental `@JsonInclude` drift.

### `SecurityService.PermissionDecision`

- `nodeId`
- `username`
- `permission`
- `allowed`
- `reason`
- `dynamicAuthority`
- `allowedAuthorities`
- `deniedAuthorities`

The test uses the controller's current-user path and locks `dynamicAuthority` as
an explicit JSON null for an ACL-deny decision.

### `PermissionSetDto`

- `name`
- `label`
- `description`
- `order`
- `permissions`

The metadata endpoint is generated directly from the `PermissionSet` enum and is
locked in enum order.

The `permission-sets` map endpoint is also covered because it is consumed by the
same frontend permission dialog. Its contract is a JSON object keyed by
permission-set name with each value serialized as a permission-name array.

## Verification

Local static hygiene:

```bash
git diff --check -- . ':!.env'
```

Result: passed.

Targeted Maven test:

```bash
cd ecm-core
./mvnw -Dtest=SecurityControllerPermissionResponseContractTest test
```

Result: blocked by the local environment before Maven startup:

```text
failed to connect to the docker API at unix:///Users/chouhua/.docker/run/docker.sock; check if the path is correct and if the daemon is running: dial unix /Users/chouhua/.docker/run/docker.sock: connect: no such file or directory
```

CI remains the authoritative execution gate for this slice.

## Expected CI Gate

After push, the required confirmation is the normal GitHub Actions matrix:

- Backend Verify
- Frontend Build & Test
- Phase C Security Verification
- Acceptance Smoke (3 admin pages)
- Property Encryption Closeout Gate
- Phase 5 Mocked Regression Gate
- Frontend E2E Core Gate

If CI is green, append a `CI Follow-Up` section with the run id and commit a
doc-only `[skip ci]` closeout.
