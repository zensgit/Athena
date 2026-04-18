# Athena Repository Kernel Hardening Development And Acceptance Plan

## Date
- 2026-04-13

## Status
- Draft

## References
- [GAP_ANALYSIS_VS_ALFRESCO_20260413.md](/Users/chouhua/Downloads/Github/Athena/docs/GAP_ANALYSIS_VS_ALFRESCO_20260413.md)
- [CLAUDE_EXECUTION_PLAN.md](/Users/chouhua/Downloads/Github/Athena/docs/CLAUDE_EXECUTION_PLAN.md)

## Background
- Athena has broad ECM feature coverage, but several repository-level correctness gaps still sit below the feature layer.
- These are higher priority than adding more integrations or protocols because they affect data safety, version history correctness, path consistency, and permission visibility.
- The immediate goal is not to clone Alfresco internals, but to harden Athena so later feature work lands on a safe repository core.

## Goals
- Eliminate P0 and P1 repository correctness risks before resuming major feature expansion.
- Introduce a minimal, Athena-native lifecycle pipeline so write paths stop depending on manual rule/index triggers.
- Re-baseline the backlog so existing partial implementations are treated as enhancement work, not greenfield work.

## Guiding Principles
- Correctness before breadth.
- Binary lifecycle must be reference-safe.
- Version history must be generated from real user content lifecycle events.
- Search visibility must converge with ACL state at transaction boundaries.
- Prefer minimal kernel abstractions that Athena can sustain over a full Alfresco-style framework rewrite.

## Pre-Implementation Source Review

### Mandatory Reads
- [ContentService.java](/Users/chouhua/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/ContentService.java:66)
- [CheckOutCheckInService.java](/Users/chouhua/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/CheckOutCheckInService.java:129)
- [NodeService.java](/Users/chouhua/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/NodeService.java:233)

### Related Reads
- [VersionService.java](/Users/chouhua/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/VersionService.java:249)
- [SecurityService.java](/Users/chouhua/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/SecurityService.java:305)
- [EcmEventListener.java](/Users/chouhua/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/event/EcmEventListener.java:29)
- [SearchIndexService.java](/Users/chouhua/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/search/SearchIndexService.java:89)

## Phase Overview

| Phase | Priority | Goal | Exit Gate |
| --- | --- | --- | --- |
| P0A | Highest | Fix binary lifecycle and check-in/version correctness | No binary data loss path remains |
| P0B | Highest | Fix subtree path consistency and ACL search visibility | DB and ES stay consistent after move/ACL changes |
| P1 | High | Add minimal lifecycle hooks, model validation, and stronger site permission model | New write paths no longer rely on ad hoc trigger wiring |
| P2 | Medium | Resume backlog on a hardened core | Feature work can reuse stable kernel services |

## P0A: Binary Lifecycle And Versioned Check-In

### Work Item P0A-1: Reference-Safe Content Lifecycle

#### Problem
- `ContentService.deleteContent` only checks active `Document.contentId` references and ignores `Version.contentId`.
- Version deletion currently tries to physically delete the binary after removing a version row.
- Shared binaries can therefore be deleted while still referenced by another version or working copy.

#### Implementation Scope
- Introduce authoritative binary reference tracking.
- Remove direct physical deletion decisions from business services.
- Convert deletion into `mark orphan candidate -> delayed cleanup verification -> physical delete`.

#### Proposed Design
- Add a `content_references` table:
  - `id`
  - `content_id`
  - `owner_type` (`DOCUMENT`, `VERSION`, `WORKING_COPY`, `RENDITION`)
  - `owner_id`
  - `active`
  - unique key on `content_id + owner_type + owner_id`
- Add a `ContentReferenceService` responsible for:
  - create/update/remove reference records transactionally
  - backfill from existing `documents` and `versions`
  - orphan detection
- Change `ContentService.deleteContent` to internal cleanup only; business services should not call it as an ownership decision.
- Add scheduled orphan cleanup with a config-driven grace period and recheck before physical delete.
- Use `WORKING_COPY` as a dedicated `owner_type`, not a `DOCUMENT` subtype convention.

#### Target Files
- `ecm-core/src/main/java/com/ecm/core/service/ContentService.java`
- `ecm-core/src/main/java/com/ecm/core/service/VersionService.java`
- `ecm-core/src/main/java/com/ecm/core/service/CheckOutCheckInService.java`
- `ecm-core/src/main/java/com/ecm/core/repository/VersionRepository.java`
- new `ContentReference*` entity/repository/service files
- `ecm-core/src/main/resources/db/changelog/changes/072-create-content-reference-ledger.xml`
- `ecm-core/src/main/resources/db/changelog/changes/073-backfill-content-references.xml`

#### Acceptance Criteria
- Deleting a non-current version does not remove the binary if any document, version, working copy, or rendition still references it.
- Reused binaries survive deletion of one referencing owner.
- Orphan binaries are deleted only after grace-period verification.
- Backfill migration creates references for all existing `Document` and `Version` rows.

#### Verification
- Unit tests for `ContentReferenceService`.
- Integration tests covering:
  - same binary referenced by 2 versions
  - same binary referenced by document and working copy
  - orphan cleanup after all references removed
- Migration verification test for backfill accuracy.

### Work Item P0A-2: Check-In Must Enter The Version Chain

#### Problem
- Current check-in writes working-copy content back to the original document and soft-deletes the working copy, but does not create a new version.

#### Implementation Scope
- Make check-in produce a new version entry whenever content or metadata changes are committed from a working copy.
- Preserve existing working-copy UX while making version history authoritative.

#### Proposed Design
- Inject `VersionService` into `CheckOutCheckInService`.
- Add a check-in flow:
  1. validate working copy ownership and locks
  2. merge working copy properties/metadata to the original
  3. create a new version from working copy content and check-in comment
  4. clear checkout state
  5. soft-delete working copy
- Keep `keepCheckedOut=true` behavior by creating a fresh working copy only after successful version creation.
- Add explicit check-in comment and major/minor version options to the API if not already exposed.

#### Target Files
- `ecm-core/src/main/java/com/ecm/core/service/CheckOutCheckInService.java`
- `ecm-core/src/main/java/com/ecm/core/service/VersionService.java`
- `ecm-core/src/main/java/com/ecm/core/controller/DocumentController.java`
- `ecm-core/src/main/java/com/ecm/core/controller/NodeContentController.java`

#### Acceptance Criteria
- Every successful check-in creates a new version history entry.
- Version number and label advance correctly.
- Revert, compare, and history endpoints continue to work after check-in-created versions.
- Failed version creation leaves original content unchanged and working copy undeleted.

#### Verification
- Integration tests for:
  - checkout -> edit -> checkin -> version count increment
  - keep checked out flow
  - rollback when version creation fails

## P0B: Subtree Consistency And ACL Search Convergence

### Work Item P0B-1: Recursive Path Consistency On Move

#### Problem
- Moving a folder currently saves the moved node but does not authoritatively refresh descendant paths in the database.

#### Implementation Scope
- Make subtree move update all descendant paths in DB and ES in one consistent operation.

#### Proposed Design
- Add explicit subtree path refresh in `NodeService.moveNode`.
- Use a dedicated repository operation or recursive service traversal to update descendant `path` values.
- Publish one subtree-aware move event so index refresh can reuse the updated DB state.

#### Target Files
- `ecm-core/src/main/java/com/ecm/core/service/NodeService.java`
- `ecm-core/src/main/java/com/ecm/core/repository/NodeRepository.java`
- `ecm-core/src/main/java/com/ecm/core/search/SearchIndexService.java`

#### Acceptance Criteria
- Moving a folder with 3+ levels of descendants updates every descendant path in DB.
- Search results show only new paths after reindex.
- No stale descendant remains under the old path prefix.

#### Verification
- Integration test:
  - create `A/B/C/doc`
  - move `A` under new parent
  - assert all descendant `path` values changed in DB
  - assert ES path values changed after commit

### Work Item P0B-2: ACL Delta Indexing

#### Problem
- Search queries already filter by indexed `permissions`, but permission mutations do not currently have a dedicated index-sync event path.

#### Implementation Scope
- Add ACL change events and targeted reindex behavior.
- Handle inheritance changes as subtree reindex triggers.

#### Proposed Design
- Introduce `NodePermissionsChangedEvent`.
- Publish it from:
  - `setPermission`
  - permission set replacement
  - inheritance enable/disable flows
- Reindex:
  - node only for direct ACL changes
  - subtree for inherited ACL boundary changes

#### Target Files
- `ecm-core/src/main/java/com/ecm/core/service/SecurityService.java`
- `ecm-core/src/main/java/com/ecm/core/event/EcmEventListener.java`
- `ecm-core/src/main/java/com/ecm/core/search/SearchIndexService.java`

#### Acceptance Criteria
- After `READ` is revoked, the affected user no longer sees the node in search after transaction commit.
- After `READ` is granted, the affected user can find the node in search after transaction commit.
- Inheritance toggles update descendant search visibility.

#### Verification
- Integration tests with two users:
  - grant -> searchable
  - revoke -> not searchable
  - inherited ACL change on parent folder updates child visibility

## P1: Lifecycle Hooks, Model Governance, Site Permission Hardening

### Work Item P1-1: Minimal Lifecycle Hook Pipeline

#### Scope
- Introduce Athena-native lifecycle hooks for:
  - `NODE_CREATED`
  - `NODE_UPDATED`
  - `NODE_MOVED`
  - `NODE_DELETED`
  - `NODE_PERMISSIONS_CHANGED`
  - `NODE_CHECKED_IN`
- Do not implement a full Alfresco policy engine in this phase.

#### Acceptance Criteria
- Rules, search index updates, audit, and notifications consume the same lifecycle stream.
- New write paths can register once instead of manually calling rule/index helpers.

### Work Item P1-2: Content Model Governance Validation

#### Scope
- Add model validation before activation, update, and delete:
  - namespace uniqueness
  - circular inheritance detection
  - type/aspect in-use checks
  - property/constraint reference checks
  - workflow definition references where applicable

#### Acceptance Criteria
- Active model elements in use cannot be deleted.
- Invalid model updates fail before persistence.
- Validation errors are explicit and API-visible.

### Work Item P1-3: Site Permission Model Hardening

#### Scope
- Replace JSONB-scanned membership request storage with first-class persistence.
- Reduce over-broad owner fallback for site collaboration operations.
- Move toward authority-backed site roles before external invitations are added.
- Adopt one-time backfill from `User.preferences.siteMembershipRequests` plus a compatibility reader for one release only.

#### Acceptance Criteria
- Site membership requests do not require scanning all users.
- Site roles are resolved from first-class membership or authority data.
- External invitation work is blocked until this model is in place.

## P2: Backlog Realignment

### Reclassified Items
- `Smart/Virtual Folders`: enhancement, not greenfield. Athena already has smart-folder runtime execution.
- `Scheduled User Actions`: enhancement, not greenfield. Athena already has cron validation and scheduled runner support.

## Current Delivery State
- `P0A`: completed
- `P0B`: completed
- `P1`: completed
- `P2` first wave: completed

## Next Wave
- `P3` is now planned separately in:
  - [P3_PR11_PR12_PR13_PR14_PR15_EXECUTION_PLAN_20260414.md](/Users/chouhua/Downloads/Github/Athena/docs/P3_PR11_PR12_PR13_PR14_PR15_EXECUTION_PLAN_20260414.md)
  - [P3_PR11_PR12_PR13_PR14_PR15_ACCEPTANCE_20260414.md](/Users/chouhua/Downloads/Github/Athena/docs/P3_PR11_PR12_PR13_PR14_PR15_ACCEPTANCE_20260414.md)
- `Generic OAuth Credential Store` is reclassified as generalization work on top of the existing mail OAuth baseline, not a true greenfield feature.
- `Disposition Schedules` is reclassified as blocked until `Legal Holds` exists.
- `Site Invitation Workflow`: depends on P1 site permission hardening.

### Deferred Until After P1
- LDAP/AD sync
- Legal holds
- disposition schedules
- records management
- plugin framework
- remote repository connector
- SMTP invitation workflow polish

## Release Gates

### Gate P0A
- All binary lifecycle tests green.
- Check-in always creates a version.
- No known path to physical binary deletion while still referenced.

### Gate P0B
- Move-subtree consistency test green in DB and ES.
- ACL grant/revoke search visibility tests green.

### Gate P1
- New write path can integrate with lifecycle hooks without manual rule/index calls.
- Model validation blocks invalid destructive operations.
- Site membership model no longer depends on JSONB preference scans.

## Definition Of Done
- Code merged with migration and rollback notes.
- Service, controller, and migration tests added.
- Search and permission acceptance cases automated.
- No feature backlog item depending on a still-open P0 or P1 gate is allowed to start.

## Recommended Next Step
1. Read the mandatory source files and confirm current behavior in code before changing schema or service flows.
2. Implement `P0A-1` and `P0A-2`.
3. Verify them with focused integration tests.
4. Only then start `P0B`.
