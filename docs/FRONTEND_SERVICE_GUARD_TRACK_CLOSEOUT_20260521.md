# Frontend Service Response-Shape Guard Track — Program Closeout

Date: 2026-05-21

## Purpose

This document closes the frontend service response-shape guard program as a
whole. It rolls up two prior closure artefacts into a single program-level
record:

- `docs/NODE_SERVICE_RESPONSE_SHAPE_GUARDS_CLOSEOUT_20260521.md` — the
  `nodeService` work (delivered as 8 planned sub-slices plus the PDF
  annotations follow-up).
- `docs/FRONTEND_SERVICE_GUARD_INVENTORY_20260521.md` — the cross-service
  inventory of the remaining 47 services.

Together they establish: **every JSON-returning method across
`ecm-frontend/src/services/*.ts` is runtime response-shape guarded.** The
guard track is complete. No further guard slices are planned.

## What the track did

The program hardened frontend service calls against malformed or
unexpected API responses — most importantly the Phase 5 Mocked harness
serving SPA `index.html` (HTTP 200) for unmocked routes, which a naive
`response.ok` / `.map()` consumer would crash on.

The shared runtime contract:

- JSON calls use `api.<verb><unknown>` (or `api.<verb><Typed>`) and then
  run a structural predicate before returning.
- A malformed response throws a service-scoped
  `*_UNEXPECTED_RESPONSE_MESSAGE` sentinel; consumers' existing
  `try/catch` / Redux-rejected / `.catch()` paths absorb it.
- Blob, download, upload, and void methods are deliberately out of scope.
- Backend timestamp drift (ISO string vs Jackson `LocalDateTime` array)
  is accepted leniently and normalized before UI mappers consume it.

## Part 1 — nodeService (8 planned sub-slices + PDF follow-up)

`nodeService` was the largest single service and was delivered as small,
independently CI-verified sub-slices:

| Subdomain | Main commit | Final CI run | Result |
|---|---|---|---|
| Relations / renditions | `4c50333` | `26098717250` | 7/7 |
| Batch-download async | `d078882` | `26133212492` | 7/7 |
| Preview-side | `be3e646` | `26136473455` | 7/7 |
| Search proper | `2dbc59b` (+ trace fixes → `488d830`) | `26169033978` | 7/7 |
| Folder / node CRUD | `e097471` (+ trace fixes → `4582588`) | `26206751483` | 7/7 |
| Lock / checkout | `3847e46` | `26209237809` | 7/7 |
| Version / history | `b5250e9` | `26210553413` | 7/7 |
| Permissions | `9976821` | `26226880018` | 7/7 |
| PDF annotations (follow-up) | `9fe8434` | `26229743558` | 7/7 |

Each sub-slice has its own design/verification doc under `docs/NODE_SERVICE_*`.

## Part 2 — Cross-service inventory (47 services)

A direct grep of every `ecm-frontend/src/services/*.ts` (excluding
`nodeService`, `api`, `authBootstrap`) classified each service by whether
its JSON methods run a runtime guard:

| Tier | Count | Meaning |
|---|--:|---|
| D — fully guarded, canonical idiom | 44 | All JSON methods use `api.<verb><unknown>` + `assert*` |
| D* — fully guarded, stylistic deviation | 1 | `siteInvitationService` — `api.<verb><SiteInvitationDto>` + real `assert*`; runtime guard still fires |
| D — fully guarded JSON + 1 Blob OOS | 1 | `opsRecoveryService` — 25/25 JSON guarded; 1 `api.post<Blob>` CSV export is OOS by design |
| no JSON surface | 1 | `authService` — token/bootstrap only, all `Promise<void>` |

**Un-guarded JSON methods across all 47 services: 0.**

Full per-service table is in
`docs/FRONTEND_SERVICE_GUARD_INVENTORY_20260521.md`.

### Inventory accuracy note

A first-pass automated scan mis-classified 45 services as "partially
guarded" (~342 supposedly un-guarded methods) by counting
`api.<verb><Typed>` calls as un-guarded without checking for a
downstream `assert*`. The authoritative count was produced by direct
grep against the actual conversion pattern. This is recorded here so a
future reader does not re-open the track on a false signal.

## Combined coverage

| Surface | State |
|---|---|
| `nodeService` JSON methods | guarded (8 sub-slices) |
| Other 47 services' JSON methods | guarded |
| Blob / download / upload / void methods | intentionally OOS |
| **Total un-guarded JSON methods, whole frontend** | **0** |

## Guarding rules that emerged (stable playbook)

If the pattern is ever extended (new service, new endpoint):

1. One service-wide sentinel constant.
2. Per-DTO predicates; share internal helpers.
3. Validate enum-like wire fields as plain strings unless the UI needs
   stricter.
4. List / paged / metadata reads are guardable JSON surfaces.
5. Blob / download / upload / void stay OOS unless scope explicitly changes.
6. Keep request-shape tests for OOS write methods sitting next to guarded
   reads, so endpoint/param drift is still caught.
7. Predicates are **lenient by default** for nullable backend wire fields
   (`string | null`, Jackson timestamp arrays). Keep a field strict only when
   the mapper or consumer truly requires it; do not assume `path` is always
   strict just because a TypeScript interface says so.
8. fix-commit stages the service file and its co-located test together.
9. Gate CI on `gh run view` conclusion, not a watch wrapper's exit code.

## Trace-driven corrections (lessons banked)

Two sub-slices needed correction after initial local-green:

- **Search proper** — predicates over-validated optional search-result
  fields the UI mapper never required; widened, ended green at run
  `26169033978`.
- **Folder / node CRUD** — Phase 5 exposed a too-thin test mock (aligned
  the mock to the service contract, did not weaken the predicate);
  E2E Core then exposed real backend drift (`queryCriteria: null`,
  `size: null`), fixed by the trace-driven narrowing playbook (capture
  failing shape → identify one field → widen only that site → add a
  regression fixture). Ended green at run `26206751483`.

The durable lesson — strict guards built from TS interfaces or Jest
mocks can over-reject real wire shapes; calibrate against the E2E gate —
is recorded in memory `feedback_guard_predicates_real_backend_shape_drift`.

## Deliberate OOS boundaries (whole frontend)

Out of scope, by design, not a regression:

- Blob / download: `downloadDocument`, `downloadNodesAsZip`,
  `downloadBatchDownloadAsyncTask`, `exportDryRunFailedPreviewsCsvBySearch`,
  `downloadDryRunFailedPreviewsCsvExportAsyncBySearch`, `downloadVersion`,
  `opsRecoveryService` CSV export, and other `api.downloadFile` /
  `api.getBlob` methods.
- Void write / delete: `unlockNode`, `unlockNodeDeep`, `deleteNode`,
  `removeTargetAssociation`, `removeSecondaryChild`, `setPermission`,
  `applyPermissionSet`, `setInheritPermissions`, `removePermission`, and
  other `Promise<void>` methods.
- Upload: `uploadDocument` validates only `success` / `documentId`, then
  defers to guarded `getNode`.

## Verification baseline

The final node-service regression sweep (permissions slice) ran 10 test
suites / 77 tests green; with the PDF-annotations follow-up the
node-service guard regression is 11 suites / 81 tests green.

Every sub-slice closed on a 7/7 GitHub Actions run (Backend Verify,
Frontend Build & Test, Phase C Security Verification, Acceptance Smoke,
Property Encryption Closeout Gate, Phase 5 Mocked Regression Gate,
Frontend E2E Core Gate). The last guard code commit, `9fe8434`, closed
on run `26229743558`, 7/7.

## Current state

- The frontend service response-shape guard track is **complete**.
- `origin/main` carries all guard slices plus the two prior closure docs.
- Working tree carries only the pre-existing local `.env` modification.

## Recommended next work — switch tracks

The guard track is closed; do **not** keep searching for guard slices.
Remaining items, if any, are optional and not track-reopening:

- Optional micro-cleanup: align `siteInvitationService`'s 5 call sites to
  the canonical `api.<verb><unknown>` idiom (no runtime change; fold into
  any future touch of that file).

The gate should now open a **different** track. Candidates worth a
scoping pass — not a commitment — include backend response-contract
testing, a different defensive layer, or a feature/capability stream.
Searching for more frontend service-guard work is explicitly not the
next step.
