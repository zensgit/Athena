# NodeController Relation Response-Contract Tests

Date: 2026-05-22

## Context

This is the follow-up NodeController response-contract slice after
`docs/NODE_CONTROLLER_RESPONSE_CONTRACT_TESTS_DESIGN_VERIFICATION_20260522.md`.

The previous slice locked the core `NodeDto` read contract for `/nodes/{id}` and
`/nodes/{id}/children`. This slice locks the inline relation records and the
imported `VersionDto` shapes used by the node relation endpoints.

## Scope

Added:

- `ecm-core/src/test/java/com/ecm/core/controller/NodeControllerRelationResponseContractTest.java`

Covered endpoints:

- `GET /api/v1/nodes/{nodeId}/relations/summary`
- `GET /api/v1/nodes/{nodeId}/relations/parents`
- `GET /api/v1/nodes/{nodeId}/relations/targets`
- `GET /api/v1/nodes/{nodeId}/relations/versions`
- `GET /api/v1/nodes/{nodeId}/relations/checkout`
- `GET /api/v1/nodes/{nodeId}/relations/checkout-graph`

Out of scope:

- Rendition relation endpoints.
- Children/source relation variants not needed to introduce new DTO shapes.
- Node create/update/move/copy/aspect write endpoints.
- Lock-info endpoint, already covered separately.
- Permission endpoints.
- Controller implementation changes.
- Frontend guard changes.

## Design

The test uses standalone `MockMvc` with mocked `NodeController` dependencies and
a Jackson `ObjectMapper` configured with `JavaTimeModule` plus
`WRITE_DATES_AS_TIMESTAMPS` disabled.

The slice locks these wire DTO field sets:

- `NodeRelationsSummaryDto`
- `NodeRelationNodeRefDto`
- `NodeRelationEdgeDto`
- `VersionDto`
- `NodeCheckoutRelationDto`
- `NodeCheckoutGraphDto`
- `NodeCheckoutGraphNodeDto`
- `NodeCheckoutGraphEdgeDto`

The test intentionally stays separate from `NodeControllerRelationsTest`.
`NodeControllerRelationsTest` remains the behavior suite; this file is the wire
contract suite and asserts complete JSON field-name lists.

The `VersionDto` assertions pin nullable optional fields as explicit JSON null
where applicable:

- `comment`
- `createdDate`
- `creator`
- `mimeType`
- `contentHash`
- `contentId`
- `status`

The checkout graph case also verifies nested graph node/edge field sets and the
nested `VersionDto` field set used by `baselineVersion` and `currentVersion`.

## Verification

Local static hygiene:

```bash
git diff --check -- . ':!.env'
```

Result: passed.

Targeted Maven test:

```bash
cd ecm-core
./mvnw -Dtest=NodeControllerRelationResponseContractTest test
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
