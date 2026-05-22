# NodeController Rendition Relation Response-Contract Tests

Date: 2026-05-22

## Context

This is the next NodeController backend response-contract slice after the core
`NodeDto` read endpoints and the first relation DTO set.

The previous relation slice intentionally left rendition relation endpoints out
of scope because they expose a separate virtual-resource DTO shape. This slice
locks those shapes explicitly.

## Scope

Added:

- `ecm-core/src/test/java/com/ecm/core/controller/NodeControllerRenditionRelationResponseContractTest.java`

Covered endpoints:

- `GET /api/v1/nodes/{nodeId}/relations/renditions/summary`
- `GET /api/v1/nodes/{nodeId}/relations/renditions`
- `GET /api/v1/nodes/{nodeId}/relations/renditions/{renditionId}`

Out of scope:

- Node relation summary / parent / edge / version / checkout endpoints already
  covered by the prior relation contract slice.
- Node create/update/move/copy/aspect endpoints.
- Lock-info and permission endpoints.
- Controller implementation changes.
- Frontend guard changes.

## Design

The test uses standalone `MockMvc` with mocked `NodeController` dependencies and
a Jackson `ObjectMapper` configured with `JavaTimeModule` plus
`WRITE_DATES_AS_TIMESTAMPS` disabled.

The slice locks two inline record wire contracts:

### `NodeRenditionRelationSummaryDto`

- `nodeId`
- `document`
- `previewStatus`
- `renditionAvailable`
- `previewFailureReason`
- `previewFailureCategory`
- `previewLastUpdated`
- `currentVersionLabel`

The folder case is included because it returns the same DTO with explicit null
preview fields and `document: false`.

### `NodeRenditionRelationDto`

- `nodeId`
- `renditionId`
- `label`
- `status`
- `available`
- `mimeType`
- `url`
- `downloadable`
- `failureReason`
- `failureCategory`
- `previewLastUpdated`
- `currentVersionLabel`

Both the paged list endpoint and the single-rendition endpoint are covered. The
single-rendition case locks explicit nulls for optional failure/timestamp/version
fields.

## Verification

Local static hygiene:

```bash
git diff --check -- . ':!.env'
```

Result: passed.

Targeted Maven test:

```bash
cd ecm-core
./mvnw -Dtest=NodeControllerRenditionRelationResponseContractTest test
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
