# nodeService Response-Shape Guards Closeout

## Context

This document closes the planned `nodeService` response-shape guard program.
The work was delivered as small, independently verified sub-slices rather than
a single large refactor. Each slice preserved endpoints, request bodies, query
params, and existing UI error handling while adding runtime validation for JSON
responses.

The shared runtime contract is:

- API calls that return JSON use `api.*<unknown>`.
- A structural predicate validates the response shape.
- Malformed JSON throws the service-wide `NODE_UNEXPECTED_RESPONSE_MESSAGE`.
- Blob, download, and void methods stay outside response-shape validation.
- Backend timestamp drift is handled leniently where already observed:
  string and Jackson `LocalDateTime` arrays are accepted and normalized before
  existing UI mappers consume them.

## Delivered Subdomains

| Subdomain | Main implementation | Final CI run | Final result | Design / verification doc |
|---|---:|---:|---|---|
| Relations / renditions | `4c50333` | `26098717250` | 7/7 green | `docs/NODE_SERVICE_RELATIONS_RENDITIONS_GUARDS_DESIGN_VERIFICATION_20260519.md` |
| Batch-download async | `d078882` | `26133212492` | 7/7 green | `docs/NODE_SERVICE_BATCH_DOWNLOAD_ASYNC_GUARDS_DESIGN_VERIFICATION_20260519.md` |
| Preview-side | `be3e646` | `26136473455` | 7/7 green | `docs/NODE_SERVICE_PREVIEW_SIDE_GUARDS_DESIGN_VERIFICATION_20260519.md` |
| Search proper | `2dbc59b` plus trace fixes | `26169033978` | 7/7 green | `docs/NODE_SERVICE_SEARCH_PROPER_GUARDS_DESIGN_VERIFICATION_20260519.md` |
| Folder / node CRUD | `e097471` plus trace fixes | `26206751483` | 7/7 green | `docs/NODE_SERVICE_FOLDER_NODE_CRUD_GUARDS_DESIGN_VERIFICATION_20260520.md` |
| Lock / checkout | `3847e46` | `26209237809` | 7/7 green | `docs/NODE_SERVICE_LOCK_CHECKOUT_GUARDS_DESIGN_VERIFICATION_20260521.md` |
| Version / history | `b5250e9` | `26210553413` | 7/7 green | `docs/NODE_SERVICE_VERSION_HISTORY_GUARDS_DESIGN_VERIFICATION_20260521.md` |
| Permissions | `9976821` | `26226880018` | 7/7 green | `docs/NODE_SERVICE_PERMISSIONS_GUARDS_DESIGN_VERIFICATION_20260521.md` |

## Final Local Verification Baseline

The last slice, permissions, ran the full node-service guard regression sweep:

```bash
cd ecm-frontend
CI=true npm test -- --runTestsByPath \
  src/services/nodeService.permissions.test.ts \
  src/services/nodeService.versionHistory.test.ts \
  src/services/nodeService.lockCheckout.test.ts \
  src/services/nodeService.relationsRenditions.test.ts \
  src/services/nodeService.folderNodeCrud.test.ts \
  src/services/nodeService.searchProper.test.ts \
  src/services/nodeService.previewSide.test.ts \
  src/services/nodeService.batchDownloadAsync.test.ts \
  src/services/nodeService.createFolder.test.ts \
  src/services/nodeService.recordProjection.test.ts \
  --watchAll=false
```

Result:

- 10 suites passed.
- 77 tests passed.

Additional final checks:

- `npm run lint`: passed.
- `CI=true npm run build`: passed. CRA emitted only the existing bundle-size
  advisory and `fs.F_OK` deprecation warning.
- `git diff --check -- . ':!.env'`: clean.

The final remote run for the last code commit was GitHub Actions run
`26226880018` on head `9976821`, and all seven CI jobs passed:

- Backend Verify
- Frontend Build & Test
- Phase C Security Verification
- Acceptance Smoke (3 admin pages)
- Property Encryption Closeout Gate
- Phase 5 Mocked Regression Gate
- Frontend E2E Core Gate

## Guarding Rules That Emerged

The stable implementation rules are now:

- Keep one service-wide sentinel: `NODE_UNEXPECTED_RESPONSE_MESSAGE`.
- Prefer per-DTO predicates with shared internal helpers.
- Validate runtime enum-like fields as strings unless the UI needs a stricter
  guarantee.
- Treat list/paged/metadata read methods as guardable JSON surfaces.
- Treat blob/download/void methods as deliberate OOS unless the scope changes.
- Keep request shape tests for OOS write methods when they sit next to guarded
  read methods, so endpoint and params drift is still caught.
- For real-backend drift, use the trace-driven narrowing playbook:
  capture the failing response shape, identify one rejected field, widen only
  that predicate site, and add a regression fixture.

## Trace-Driven Corrections

Two subdomains needed real-backend or mocked-gate correction after initial
local green results.

Search proper:

- Initial predicate assumptions were too strict for real search-result wire
  shape.
- The final fix path ended at run `26169033978` on head `488d830`.
- The key lesson was to avoid over-validating optional search-result fields
  that are not required by the UI mapper.

Folder / node CRUD:

- Phase 5 mocked regression exposed a too-thin mock for `/api/v1/nodes/doc-1`;
  the mock was aligned with the service contract rather than weakening the
  production predicate.
- E2E Core then exposed real backend drift:
  `queryCriteria: null` on folder roots and `size: null` on folder items.
- The final fix path ended at run `26206751483` on head `4582588`.
- The key lesson was to make predicates lenient by default for nullable backend
  wire fields while keeping true identifiers strict.

## Deliberate OOS Boundaries

The following method categories remain intentionally outside this response-shape
guard program:

- Blob / download:
  `downloadDocument`, `downloadNodesAsZip`, `downloadBatchDownloadAsyncTask`,
  `exportDryRunFailedPreviewsCsvBySearch`,
  `downloadDryRunFailedPreviewsCsvExportAsyncBySearch`, `downloadVersion`.
- Void write / delete:
  `unlockNode`, `unlockNodeDeep`, `deleteNode`, `removeTargetAssociation`,
  `removeSecondaryChild`, `setPermission`, `applyPermissionSet`,
  `setInheritPermissions`, `removePermission`.
- Upload response:
  `uploadDocument` still validates only `success` and `documentId`, then uses
  guarded `getNode` for the returned node.
- PDF annotation JSON:
  `getPdfAnnotations` and `savePdfAnnotations` were not part of the planned
  node-service guard backlog. If the objective becomes "every JSON method",
  annotations were completed as follow-up commit `9fe8434` and documented in
  `docs/NODE_SERVICE_PDF_ANNOTATIONS_GUARDS_DESIGN_VERIFICATION_20260521.md`.

These are not CI regressions. They are explicit scope boundaries from the
incremental guard program.

## Current State

The planned `nodeService` JSON response-shape guard backlog is complete.

Current terminal state at closeout:

- Latest code slice: `9976821 fix(frontend): guard node permission service responses`
- Latest CI-record doc commit before this closeout: `447439f docs(services): record CI for node permission guards [skip ci]`
- Last full CI run: `26226880018`, 7/7 success
- Known local-only working tree item: `.env`

## Recommended Next Work

If we continue service-contract hardening, use this priority:

1. Produce a cross-service guard inventory for `ecm-frontend/src/services/*.ts`
   to identify the next highest-risk unguarded service.
2. Defer blob/download validation unless there is a concrete browser/runtime
   failure, because the current guard idiom is JSON-response specific.
