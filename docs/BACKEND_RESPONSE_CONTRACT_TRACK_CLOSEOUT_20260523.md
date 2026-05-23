# Backend Response-Contract Test Track Closeout

Date: 2026-05-23

## Status

The backend response-contract test track is complete for the Top 10 endpoint
groups identified in `BACKEND_RESPONSE_CONTRACT_TEST_TODO_20260521.md`.

The Top 10 groups were delivered as 12 mergeable slices because `NodeController`
was split into three contract surfaces:

- node core read contracts;
- node relation contracts;
- node rendition-relation contracts.

Every shipped slice has a final GitHub Actions run with all seven repository
gates green. The only local working-tree change left outside this track is the
pre-existing `.env` modification.

## Delivered Slices

| # | Slice | Primary doc | Final gated head | Final CI |
|---|-------|-------------|------------------|----------|
| 1 | FolderController browsing | `FOLDER_CONTROLLER_RESPONSE_CONTRACT_TESTS_DESIGN_VERIFICATION_20260521.md` | `2c32685` | `26272387259` |
| 2 | SearchController search result/envelope | `SEARCH_CONTROLLER_RESPONSE_CONTRACT_TESTS_DESIGN_VERIFICATION_20260522.md` | `7379bd8` | `26275023955` |
| 3 | NodeController core read | `NODE_CONTROLLER_RESPONSE_CONTRACT_TESTS_DESIGN_VERIFICATION_20260522.md` | `9a3232c` | `26276950386` |
| 4 | NodeController relations | `NODE_CONTROLLER_RELATION_RESPONSE_CONTRACT_TESTS_DESIGN_VERIFICATION_20260522.md` | `8e71e46` | `26285831946` |
| 5 | NodeController rendition relations | `NODE_CONTROLLER_RENDITION_RELATION_RESPONSE_CONTRACT_TESTS_DESIGN_VERIFICATION_20260522.md` | `ec4f6d0` | `26287407186` |
| 6 | SecurityController permissions | `SECURITY_CONTROLLER_PERMISSION_RESPONSE_CONTRACT_TESTS_DESIGN_VERIFICATION_20260522.md` | `170202d7961cc9a97c193cb20e7a9d12503538e7` | `26318661729` |
| 7 | PreviewDiagnosticsController high-traffic reads | `PREVIEW_DIAGNOSTICS_RESPONSE_CONTRACT_TESTS_DESIGN_VERIFICATION_20260522.md` | `6be65e5e6df1e479fec5267069afa545f14ef02d` | `26319556549` |
| 8 | RecordsManagementController core reads | `RECORDS_MANAGEMENT_RESPONSE_CONTRACT_TESTS_DESIGN_VERIFICATION_20260522.md` | `b03fe9b6b309dfd6cc5419b3dd595e8af112c20f` | `26326650362` |
| 9 | DocumentController versions/checkout-info | `DOCUMENT_CONTROLLER_VERSION_CHECKOUT_RESPONSE_CONTRACT_TESTS_DESIGN_VERIFICATION_20260523.md` | `00eab44730a8c8a41db7e75e8d1328cd2647736c` | `26327567298` |
| 10 | OpsRecoveryController async export lifecycle | `OPS_RECOVERY_RESPONSE_CONTRACT_TESTS_DESIGN_VERIFICATION_20260523.md` | `46dc10d1f66b348885b889ac77f7413bb9497850` | `26329502441` |
| 11 | WorkflowController high-consumption reads | `WORKFLOW_CONTROLLER_RESPONSE_CONTRACT_TESTS_DESIGN_VERIFICATION_20260523.md` | `4232075e2117945e639776ec4c32cb68f24816a1` | `26332615842` |
| 12 | MailAutomationController high-traffic reads | `MAIL_AUTOMATION_RESPONSE_CONTRACT_TESTS_DESIGN_VERIFICATION_20260523.md` | `6bda32d5e0dc7d1123915bbd7bbebeae525a7740` | `26333415170` |

Each final CI run passed:

- Backend Verify
- Frontend Build & Test
- Phase C Security Verification
- Frontend E2E Core Gate
- Phase 5 Mocked Regression Gate
- Acceptance Smoke (3 admin pages)
- Property Encryption Closeout Gate

## Coverage Summary

The track locks the highest-risk backend wire contracts that the frontend
service-guard track depends on:

- proven frontend drift surfaces: folder roots/contents, search results, and
  node size/path/nullability variants;
- high-blast-radius node contracts: core `NodeDto`, relation records, rendition
  relation records, version history, and checkout info;
- admin dashboard/read-heavy controllers: preview diagnostics, records
  management, ops recovery, workflow, and mail automation;
- permission decision/read contracts used by `nodeService`.

The tests intentionally favor JSON field-set locks and explicit `null` checks
over broad behavior assertions. That keeps this track focused on preventing
wire-shape drift rather than duplicating service behavior tests.

## Forward-Fix History

The track used forward fixes only. No force-push or history rewrite was needed.

| Slice | Fix commit | Classification | Root cause |
|-------|------------|----------------|------------|
| FolderController | `2c32685` | Contract alignment | The test asserted a stale boolean field name. The final contract locks the actual serialized field. |
| NodeController relations | `8e71e46` | Contract alignment | The version relation fixture expected the wrong `size`; the final contract locks the `Version.fileSize` value actually serialized by `VersionDto`. |
| RecordsManagementController | `b03fe9b` | Test fixture alignment | The initial test used the wrong `PageImpl` constructor ordering for the audit page envelope. |
| OpsRecoveryController | `490a655` | Test fixture alignment | The async latch watched the unfiltered HISTORY query path while the request used actor/event filters and routed to a different repository method. |
| OpsRecoveryController | `46dc10d` | Contract discovery | `createdBy` is the request actor, but `updatedBy` on the async worker transition is `system` because the worker has no request security context. |

The OpsRecovery worker-actor finding is the most reusable lesson from the
track: lifecycle tests that cross async thread boundaries must not assume the
request actor and worker actor are identical.

## Test Infrastructure Evolution

Most slices use standalone `MockMvc` with mocked controller dependencies. That
was sufficient for pure serialization contracts and kept tests fast and narrow.

`OpsRecoveryControllerResponseContractTest` deliberately uses
`@WebMvcTest(OpsRecoveryController.class)` because the contract depends on two
Spring-managed behaviors:

- request security context for `createdBy`;
- async worker execution for the `updatedBy = system` transition.

This split is now the preferred decision rule:

- Use standalone `MockMvc` when the wire shape is derived from DTO mapping only.
- Use `@WebMvcTest` when the response contract depends on method security,
  request identity, filters, or async execution.

## Deliberate Out Of Scope

The track does not claim exhaustive backend API coverage. It closes the
high-risk response-contract plan seeded by the frontend service-guard closeout.

Intentionally out of scope:

- CSV/blob/download endpoints.
- Pure mutation endpoints whose response body is empty or already covered by
  service tests.
- Lower-traffic child endpoints left as documented follow-ups in each slice.
- Controllers outside the Top 10 risk inventory.
- Frontend predicate changes.

Any future backend response-contract work should start from an observed drift,
a new frontend guard failure, or a new high-traffic feature surface. Do not
reopen this track just to search for more broad contract work.

## Local Verification

Local static hygiene for the closeout doc:

```bash
git diff --check -- . ':!.env'
```

Result: passed.

Local targeted Maven execution remains blocked in this environment before Maven
startup because Docker is not reachable:

```text
failed to connect to the docker API at unix:///Users/chouhua/.docker/run/docker.sock; check if the path is correct and if the daemon is running: dial unix /Users/chouhua/.docker/run/docker.sock: connect: no such file or directory
```

For code slices in this track, GitHub Actions was the authoritative execution
gate.

## Final Recommendation

Mark the backend response-contract track closed.

The next engineering track should be scoped from product value or a new
operational risk, not from more generic contract-test expansion. Candidate
directions include:

- contract tests for a newly changed backend feature;
- deeper protocol endpoint security hardening;
- feature delivery outside the guard/contract theme.
