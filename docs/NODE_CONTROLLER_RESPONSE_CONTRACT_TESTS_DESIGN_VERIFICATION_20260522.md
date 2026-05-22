# NodeController Response-Contract Tests

Date: 2026-05-22

## Context

This is the third backend response-contract implementation slice from
`docs/BACKEND_RESPONSE_CONTRACT_TEST_TODO_20260521.md`, after the
FolderController and SearchController slices.

The frontend service guard track previously needed trace-driven fixes for
folder/node browsing shapes. In particular, folder rows can serialize `size` as
JSON `null`. That shape was already locked for `FolderController.NodeResponse`,
but `NodeController` exposes a distinct `NodeDto` wire contract, so it needs its
own MockMvc lock.

## Scope

Added:

- `ecm-core/src/test/java/com/ecm/core/controller/NodeControllerResponseContractTest.java`

Covered endpoints:

- `GET /api/v1/nodes/{nodeId}`
- `GET /api/v1/nodes/{nodeId}/children`

Out of scope:

- Node create/update/move/copy/aspect write endpoints.
- Node relation endpoints.
- Lock/checkout endpoints.
- Permission endpoints.
- Controller implementation changes.
- Frontend guard changes.

## Design

The test uses standalone `MockMvc` with mocked `NodeController` dependencies and
a Jackson `ObjectMapper` configured with `JavaTimeModule` plus
`WRITE_DATES_AS_TIMESTAMPS` disabled. This mirrors the ISO `LocalDateTime`
shape used by the prior FolderController and SearchController contract tests.

The slice locks the current `NodeDto` wire field set:

- `id`
- `name`
- `description`
- `path`
- `nodeType`
- `typeQName`
- `parentId`
- `size`
- `contentType`
- `currentVersionLabel`
- `correspondentId`
- `correspondentName`
- `properties`
- `metadata`
- `aspects`
- `tags`
- `categories`
- `inheritPermissions`
- `locked`
- `lockedBy`
- `lockedDate`
- `lockLifetime`
- `lockExpiresAt`
- `checkedOut`
- `checkoutUser`
- `checkoutDate`
- `workingCopyOf`
- `isWorkingCopy`
- `createdBy`
- `createdDate`
- `lastModifiedBy`
- `lastModifiedDate`
- `previewStatus`
- `previewFailureReason`
- `previewFailureCategory`
- `previewLastUpdated`

The boolean record component is intentionally asserted as `isWorkingCopy`. A
direct Jackson check against the compiled `NodeDto.class` confirmed the property
name stays `isWorkingCopy`, not `workingCopy`.

The test also locks the distinction between `NodeDto` and folder-specific DTOs:

- folder rows keep `size` present as JSON `null`;
- `queryCriteria` is absent from `NodeDto`, even for `Folder` entities;
- `properties` and `metadata` serialize as JSON objects;
- `aspects`, `tags`, and `categories` serialize as arrays.

## Verification

Local static hygiene:

```bash
git diff --check -- . ':!.env'
```

Result: passed.

Jackson wire-shape check for `NodeDto`:

```bash
jshell --class-path "ecm-core/target/classes:...jackson jars..."
```

Result: `NodeDto` serializes 36 properties in record order, including
`isWorkingCopy`.

Targeted Maven test:

```bash
cd ecm-core
./mvnw -Dtest=NodeControllerResponseContractTest test
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

## CI Follow-Up

Final CI:

- GitHub Actions run `26276950386`
- Head: `9a3232c`
- Result: `success`

All seven jobs passed:

- Backend Verify
- Frontend Build & Test
- Phase C Security Verification
- Acceptance Smoke (3 admin pages)
- Property Encryption Closeout Gate
- Phase 5 Mocked Regression Gate
- Frontend E2E Core Gate
