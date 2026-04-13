# Athena Repository Kernel Hardening Task Breakdown

## Date
- 2026-04-13

## Status
- Draft

## References
- [ATHENA_REPOSITORY_KERNEL_HARDENING_DEV_AND_ACCEPTANCE_20260413.md](/Users/chouhua/Downloads/Github/Athena/docs/ATHENA_REPOSITORY_KERNEL_HARDENING_DEV_AND_ACCEPTANCE_20260413.md)

## Goal
- Convert the kernel hardening plan into assignable engineering tasks.
- Keep scope small enough for reviewable PRs.
- Prevent P2 feature work from starting before P0 and P1 gates are satisfied.

## Delivery Rules
- One task should preferably map to one PR unless the write set is too tightly coupled.
- Every data model task must include Liquibase work and migration verification.
- Every search or ACL task must include integration coverage against transaction boundaries.
- No task is considered complete without explicit regression notes.

## Pre-Task Mandatory Review
- [ContentService.java](/Users/chouhua/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/ContentService.java:66)
- [CheckOutCheckInService.java](/Users/chouhua/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/CheckOutCheckInService.java:129)
- [NodeService.java](/Users/chouhua/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/NodeService.java:233)

## Phase P0A

### Task P0A-1: Introduce Content Reference Ledger

#### Objective
- Create authoritative binary ownership records independent of `Document` and `Version` business flows.

#### Write Scope
- new `ContentReference` entity
- new `ContentReferenceRepository`
- new `ContentReferenceService`
- `ecm-core/src/main/resources/db/changelog/changes/072-create-content-reference-ledger.xml`

#### Main Changes
- Add `content_references` persistence model.
- Define owner types: `DOCUMENT`, `VERSION`, `WORKING_COPY`, `RENDITION`.
- Add APIs to attach, detach, and inspect references.
- Use config-driven orphan cleanup grace period rather than DB-stored grace configuration.

#### Dependencies
- None

#### Exit Criteria
- Service can create and remove reference rows idempotently.
- Unique ownership constraints are enforced.

### Task P0A-2: Backfill Existing Content Owners

#### Objective
- Ensure existing `documents` and `versions` are represented in the new ledger.

#### Write Scope
- `ecm-core/src/main/resources/db/changelog/changes/073-backfill-content-references.xml`
- migration verification test

#### Main Changes
- Backfill `Document.contentId` references.
- Backfill `Version.contentId` references.
- Skip null or blank content ids safely.

#### Dependencies
- `P0A-1`

#### Exit Criteria
- Row counts match expected existing owners.
- Re-running backfill is safe or explicitly blocked.

### Task P0A-3: Refactor Binary Delete Semantics

#### Objective
- Stop business services from directly deciding physical binary deletion.

#### Write Scope
- `ContentService`
- `VersionService`
- `CheckOutCheckInService`
- any cleanup scheduler introduced in this phase

#### Main Changes
- Replace direct delete calls with reference detachment.
- Add orphan candidate marking.
- Add delayed cleanup verification and physical delete.

#### Dependencies
- `P0A-1`
- `P0A-2`

#### Exit Criteria
- Deleting one version never deletes a still-referenced binary.
- Cleanup only removes binaries with zero active references.

### Task P0A-4: Make Check-In Create Versions

#### Objective
- Ensure working-copy check-in becomes a first-class versioning operation.

#### Write Scope
- `CheckOutCheckInService`
- `VersionService`
- relevant controller DTOs/endpoints

#### Main Changes
- Inject `VersionService` into check-in flow.
- Create version from working-copy content during check-in.
- Preserve rollback safety on version failure.

#### Dependencies
- `P0A-3`

#### Exit Criteria
- Successful check-in increments version history.
- Failed check-in does not mutate original content.

## Phase P0B

### Task P0B-1: Persist Descendant Paths On Folder Move

#### Objective
- Make subtree path updates explicit and deterministic.

#### Write Scope
- `NodeService`
- `NodeRepository`
- supporting helper/service code for subtree traversal or bulk update

#### Main Changes
- Add descendant path refresh after move.
- Ensure DB state is correct before search reindex runs.

#### Dependencies
- None

#### Exit Criteria
- All descendants under a moved folder have updated persisted `path`.

### Task P0B-2: Reindex Moved Subtrees From Fresh DB State

#### Objective
- Align search index path data with the persisted subtree state after move.

#### Write Scope
- `EcmEventListener`
- `SearchIndexService`

#### Main Changes
- Reindex moved node after commit.
- Reindex descendants from DB, not stale in-memory graph state.

#### Dependencies
- `P0B-1`

#### Exit Criteria
- Search path fields match DB path fields after subtree move.

### Task P0B-3: Add Permission Change Event

#### Objective
- Establish a dedicated event path for ACL changes.

#### Write Scope
- new `NodePermissionsChangedEvent`
- `SecurityService`
- `EcmEventListener`

#### Main Changes
- Publish event on direct ACL update.
- Publish event on inheritance boundary changes.

#### Dependencies
- None

#### Exit Criteria
- Direct ACL changes always emit one deterministic event.

### Task P0B-4: Reindex ACL Delta

#### Objective
- Update search permission visibility immediately after ACL changes commit.

#### Write Scope
- `SearchIndexService`
- `SecurityService`
- optional subtree helper code

#### Main Changes
- Reindex node for direct ACL changes.
- Reindex subtree for inherited ACL changes.

#### Dependencies
- `P0B-3`

#### Exit Criteria
- Search visibility converges with ACL state after commit.

## Phase P1

### Task P1-1: Introduce Minimal Lifecycle Hook Contract

#### Objective
- Replace scattered manual trigger calls with a small shared lifecycle contract.

#### Write Scope
- new lifecycle event types or registry interfaces
- `NodeService`
- `VersionService`
- `CheckOutCheckInService`
- `EcmEventListener`

#### Main Changes
- Standardize hook points for create, update, move, delete, permissions changed, and check-in.
- Route rule trigger, audit, notification, and indexing through the same event stream where practical.

#### Dependencies
- P0 complete

#### Exit Criteria
- New write path can hook into one lifecycle surface instead of duplicating triggers.

### Task P1-2: Add Model Validation Service

#### Objective
- Prevent invalid model activation and destructive operations.

#### Write Scope
- new `RuntimeModelValidationService`
- `ContentModelService`
- controller error mapping

#### Main Changes
- namespace checks
- circular inheritance detection
- in-use checks for type, aspect, property, constraint
- workflow reference checks where applicable

#### Dependencies
- None

#### Exit Criteria
- Invalid delete/update/activate requests fail before persistence.

### Task P1-3: Replace JSONB Site Request Scans

#### Objective
- Move site membership requests to first-class persistence.

#### Write Scope
- new membership request entity/repository/service updates
- `SiteMembershipService`
- `PeopleController`
- `SiteController`
- `ecm-core/src/main/resources/db/changelog/changes/074-create-site-membership-requests.xml`

#### Main Changes
- stop scanning all users' preference payloads
- store request rows directly
- add indexes for `site_id`, `username`, `status`
- keep a compatibility reader for one release after one-time backfill

#### Dependencies
- None

#### Exit Criteria
- membership requests no longer depend on `User.preferences`

### Task P1-4: Narrow Owner Fallback For Collaboration Paths

#### Objective
- Remove over-broad creator-is-owner behavior from site collaboration and permission-sensitive operations.

#### Write Scope
- `SecurityService`
- affected site collaboration service/controller flows
- permission tests

#### Main Changes
- keep necessary owner shortcuts only where explicitly justified
- route site access through role/member/authority rules instead

#### Dependencies
- `P1-3`

#### Exit Criteria
- creator fallback is no longer a blanket allow-all path for collaboration features.

## Suggested PR Grouping

| PR | Tasks | Reason |
| --- | --- | --- |
| PR-1 | `P0A-1`, `P0A-2` | shared schema foundation |
| PR-2 | `P0A-3` | binary lifecycle semantics |
| PR-3 | `P0A-4` | check-in/version behavior |
| PR-4 | `P0B-1`, `P0B-2` | move consistency and search sync |
| PR-5 | `P0B-3`, `P0B-4` | ACL delta indexing |
| PR-6 | `P1-1`, `P1-2` | kernel hook and governance |
| PR-7 | `P1-3`, `P1-4` | site permission model hardening |

## Blocking Rules
- `P2` feature work cannot start before `PR-5` is complete.
- External invitation work cannot start before `PR-7` is complete.
- Legal hold or records-management work cannot start before `PR-2` and `PR-3` are complete.

## Recommended Execution Order
1. `PR-1`
2. `PR-2`
3. `PR-3`
4. `PR-4`
5. `PR-5`
6. `PR-6`
7. `PR-7`
