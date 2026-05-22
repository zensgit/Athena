# FolderController Response-Contract Tests

Date: 2026-05-21

## Context

This is the first implementation slice from
`docs/BACKEND_RESPONSE_CONTRACT_TEST_TODO_20260521.md`.

The frontend guard closeout exposed two backend response-shape details that
were previously only caught by E2E:

- `/api/v1/folders/roots` returns `FolderController.FolderResponse`, where
  `queryCriteria` is present and may be JSON `null`.
- `/api/v1/folders/{id}/contents` returns `Page<FolderController.NodeResponse>`,
  where folder rows carry `size: null`.

The goal of this slice is to pin those shapes on the Spring side so future
backend changes cannot silently omit or reshape the nullable fields that the
frontend now accepts.

## Scope

Added:

- `ecm-core/src/test/java/com/ecm/core/controller/FolderControllerResponseContractTest.java`

Covered endpoints:

- `GET /api/v1/folders/roots`
- `GET /api/v1/folders/{folderId}/contents`

Out of scope:

- Other `FolderController` endpoints.
- `NodeController` `/nodes/{id}/children`; that uses `NodeDto`, not
  `FolderController.NodeResponse`, and is tracked as a separate TODO slice.
- Frontend predicate changes.
- Production controller or DTO changes.

## Design

The test uses standalone `MockMvc` with mocked `FolderService` and `NodeService`.
It intentionally exercises the controller records and Jackson serialization
boundary, not repository or service behavior.

`ObjectMapper` is configured with `JavaTimeModule` and
`WRITE_DATES_AS_TIMESTAMPS` disabled so the standalone test matches Spring Boot's
ISO `LocalDateTime` JSON output rather than Jackson's raw array form.

Assertions are endpoint-scoped:

- `/folders/roots` asserts the flat `FolderResponse` array shape, including
  `queryCriteria: null`, and asserts `size` is absent because `FolderResponse`
  has no `size` field.
- `/folders/{id}/contents` asserts the `Page<NodeResponse>` envelope and
  `content[0].size: null`, and asserts `queryCriteria` is absent because
  `NodeResponse` has no `queryCriteria` field.

## Verification

Local static hygiene:

```bash
git diff --check -- . ':!.env'
```

Result: passed.

Targeted Maven test attempted through the repository wrapper:

```bash
cd ecm-core
./mvnw -Dtest=FolderControllerResponseContractTest test
```

Result: blocked by local environment before Maven startup:

```text
failed to connect to the docker API at unix:///Users/chouhua/.docker/run/docker.sock; check if the path is correct and if the daemon is running: dial unix /Users/chouhua/.docker/run/docker.sock: connect: no such file or directory
```

Global `mvn` is not installed on this machine. A temporary local Maven download
was attempted but cancelled because the mirror was too slow to complete within a
reasonable validation window. CI remains the authoritative execution gate for
this slice.

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
