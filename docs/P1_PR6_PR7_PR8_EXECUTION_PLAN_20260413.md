# P1 PR-6 / PR-7 / PR-8 Execution Plan

## Date
- 2026-04-13

## Status
- `PR-6` completed
- `PR-7` completed
- `PR-8` completed

## Context
- `P0A` and `P0B` are now closed from a kernel-correctness perspective.
- Backend test execution is available through [ecm-core/mvnw](/Users/chouhua/Downloads/Github/Athena/ecm-core/mvnw:1).
- `P1` should now be split into three reviewable PRs instead of the older coarse two-PR grouping.

## References
- [ATHENA_REPOSITORY_KERNEL_HARDENING_DEV_AND_ACCEPTANCE_20260413.md](/Users/chouhua/Downloads/Github/Athena/docs/ATHENA_REPOSITORY_KERNEL_HARDENING_DEV_AND_ACCEPTANCE_20260413.md)
- [ATHENA_REPOSITORY_KERNEL_HARDENING_TASK_BREAKDOWN_20260413.md](/Users/chouhua/Downloads/Github/Athena/docs/ATHENA_REPOSITORY_KERNEL_HARDENING_TASK_BREAKDOWN_20260413.md)
- [P0B_PR4_PR5_REVIEW_SUMMARY_20260413.md](/Users/chouhua/Downloads/Github/Athena/docs/P0B_PR4_PR5_REVIEW_SUMMARY_20260413.md)

## Execution Model

### PR Mapping

| PR | Scope | Primary write set | Can run in parallel |
| --- | --- | --- | --- |
| PR-6 | Minimal lifecycle hook pipeline | `NodeService`, `FolderService`, `VersionService`, `CheckOutCheckInService`, `EcmEventListener`, new lifecycle publisher classes | Yes, with `PR-7` |
| PR-7 | Runtime content model governance validation | `ContentModelService`, `ContentModelController`, `NodeRepository`, model repositories, new validation service/classes | Yes, with `PR-6` |
| PR-8 | Site membership persistence and authority hardening | `SiteMembershipService`, `SiteController`, `PeopleController`, `SecurityService`, new membership-request persistence, `074` changelog | After `PR-6` and `PR-7` are scoped; can implement mostly in parallel if file ownership is kept separate |

### Why This Split
- `PR-6` is kernel/event plumbing and touches repository write paths.
- `PR-7` is governance logic and mostly isolated to content-model services and validation queries.
- `PR-8` changes persistence, API behavior, and collaboration authorization; it deserves its own migration and acceptance gate.

## PR-6: Minimal Lifecycle Hook Pipeline

### Problem
- Core write paths still mix:
  - direct domain event publishing
  - direct rule triggering
  - ad hoc audit/index/notification fan-out assumptions
- Examples already visible in the current code:
  - `NodeService.updateNode(...)` publishes `NodeUpdatedEvent` and separately calls `triggerRulesForDocument(...)`
  - `NodeService.moveNode(...)` publishes `NodeMovedEvent` and separately triggers rules
  - `BulkMetadataService` still publishes `NodeUpdatedEvent` manually
  - `VersionService.createVersion(...)` publishes `VersionCreatedEvent` and separately triggers rules

### Goal
- Give Athena one minimal lifecycle surface for repository write paths without attempting an Alfresco-scale policy framework rewrite.

### Proposed Design

#### 1. Introduce a minimal lifecycle contract
- Add:
  - `RepositoryLifecycleAction`
  - `RepositoryLifecycleEvent`
  - `RepositoryLifecyclePublisher`
- The event payload should carry only what downstream consumers actually need:
  - `nodeId`
  - `documentId` when available
  - `action`
  - `username`
  - optional flags such as `permanentDelete`, `includeDescendants`, `majorVersion`, `ruleTriggerType`
  - optional references such as `oldParentId`, `newParentId`, `versionId`

#### 2. Keep existing domain events as compatibility signals
- Do not delete `NodeCreatedEvent`, `NodeUpdatedEvent`, `NodeMovedEvent`, `VersionCreatedEvent` in `P1`.
- `RepositoryLifecyclePublisher` should publish:
  - the existing domain event needed by existing listeners
  - one unified lifecycle event for downstream rule/audit/index integration
- This keeps current listeners working while removing scattered manual trigger calls from services.

#### 3. Move rule dispatch behind the lifecycle surface
- Create one lifecycle listener dedicated to rule dispatch:
  - `NODE_UPDATED -> DOCUMENT_UPDATED`
  - `NODE_MOVED -> DOCUMENT_MOVED`
  - `NODE_CHECKED_IN -> VERSION_CREATED` or dedicated `CHECKED_IN`
  - `VERSION_CREATED -> VERSION_CREATED`
- `NodeService`, `VersionService`, and `CheckOutCheckInService` stop calling `triggerRulesForDocument(...)` directly.

#### 4. Convert the first wave of write paths
- Required conversions:
  - `NodeService.createNode/updateNode/moveNode/deleteNode`
  - `FolderService.create/update/delete`
  - `VersionService.createVersion/revert/delete`
  - `CheckOutCheckInService.checkin`
  - `SecurityService.publishPermissionsChangedEvent` path
  - `BulkMetadataService` so it does not emit a duplicate update event outside the shared path

#### 5. Keep listener responsibilities narrow
- `EcmEventListener` should remain the place for:
  - audit logging
  - search indexing
  - preview/OCR enqueue
  - notifications
- The new lifecycle listener should not reimplement those behaviors; it should standardize the invocation path and remove duplicate service-level wiring.

### Target Files
- `ecm-core/src/main/java/com/ecm/core/service/NodeService.java`
- `ecm-core/src/main/java/com/ecm/core/service/FolderService.java`
- `ecm-core/src/main/java/com/ecm/core/service/VersionService.java`
- `ecm-core/src/main/java/com/ecm/core/service/CheckOutCheckInService.java`
- `ecm-core/src/main/java/com/ecm/core/service/BulkMetadataService.java`
- `ecm-core/src/main/java/com/ecm/core/event/EcmEventListener.java`
- new `ecm-core/src/main/java/com/ecm/core/event/RepositoryLifecycle*.java`
- new dedicated lifecycle rule-dispatch listener if needed

### Non-Goals
- No full Alfresco-style policy registry.
- No rewrite of comment/tag/domain-specific rule events in this PR.
- No changeset table for lifecycle history.

### PR-6 Merge Gate
- A new write path can publish one lifecycle event instead of manually wiring rule/index behavior.
- Node update and version creation no longer call rule dispatch directly from the service layer.
- No duplicate audit/index invocation appears for bulk metadata updates.
- Status: passed

## PR-7: Runtime Content Model Governance Validation

### Problem
- `ContentModelService` currently protects only duplicate prefix/namespace at create time.
- Activation, deletion, and hierarchy updates still allow invalid or destructive model mutations:
  - active in-use definitions can be deleted
  - circular parent references are not blocked
  - property and aspect removal does not check live node usage
  - workflow/rule references are not validated before destructive changes

### Goal
- Make invalid model changes fail before persistence while reusing existing storage rather than introducing heavy new governance tables.

### Proposed Design

#### 1. Add `RuntimeModelValidationService`
- New service responsibilities:
  - validate model create/update/activate/delete
  - validate type/aspect/property/constraint delete operations
  - return explicit violations with stable messages

#### 2. Validation rules for `ContentModelDefinition`
- prefix uniqueness
- namespace uniqueness
- only `DRAFT` or `DISABLED` models can be structurally modified
- activation fails if any contained type/aspect/property hierarchy is invalid

#### 3. Validation rules for `TypeDefinition` and `AspectDefinition`
- reject circular inheritance via `parentName`
- reject parent references that resolve to missing definitions
- block delete when still referenced by live nodes:
  - `Node.typeQName`
  - `Node.aspects`

#### 4. Validation rules for `PropertyDefinition` and `ConstraintDefinition`
- block property delete when any live node stores the qualified property key in `nodes.properties`
- block constraint delete when protected by active model semantics or referenced property requirements
- keep initial implementation query-based:
  - no new persistence by default
  - native JSONB existence query is acceptable for `P1`

#### 5. Workflow and automation reference checks
- Before deleting definitions that can be referenced by automation:
  - inspect `AutomationRule` actions for `workflowKey`
  - reject destructive change when a live automation rule still depends on the removed definition or expected property payload
- `P1` only needs runtime validation, not a precomputed dependency graph.

#### 6. API error mapping
- Existing `RestExceptionHandler` already maps `IllegalArgumentException` to `400`.
- If detailed violations are needed, introduce:
  - `ModelValidationException extends IllegalArgumentException`
  - optional `violations` list exposed through `RestExceptionHandler`

### Repository Support Expected
- Add minimal queries to `NodeRepository`:
  - count live nodes by `typeQName`
  - count live nodes by aspect name
  - count live nodes containing a qualified property key in `properties`
- Add any focused repository helpers needed for rule/workflow reference checks.

### Target Files
- `ecm-core/src/main/java/com/ecm/core/service/ContentModelService.java`
- `ecm-core/src/main/java/com/ecm/core/controller/ContentModelController.java`
- `ecm-core/src/main/java/com/ecm/core/controller/RestExceptionHandler.java`
- `ecm-core/src/main/java/com/ecm/core/repository/NodeRepository.java`
- model repositories as needed
- new `ecm-core/src/main/java/com/ecm/core/service/RuntimeModelValidationService.java`
- optional new exception/dto classes for validation output

### Non-Goals
- No visual model editor work.
- No import/export workflow in this PR.
- No dictionary cache refactor beyond what validation needs.

### PR-7 Merge Gate
- Invalid activate/update/delete operations fail before persistence.
- Circular inheritance is blocked.
- In-use type/aspect/property delete is blocked with explicit API-visible error text.
- Status: passed

## PR-8: Site Membership Persistence And Authority Hardening

### Problem
- Site membership requests are still stored in `User.preferences.siteMembershipRequests`.
- `SiteMembershipService.getRequestsForSite(...)` scans all users.
- `PeopleController` contains separate moderation logic that edits preference JSON directly.
- Collaboration authorization is still too coarse:
  - member management is admin-only
  - site-specific role checks are not first-class
  - the system still leans on generic creator/owner semantics in places where explicit site roles should decide access

### Goal
- Move site request state to first-class persistence and make site-collaboration access depend on site membership roles rather than ad hoc preference or owner behavior.

### Proposed Design

#### 1. Add first-class membership request persistence
- New entity:
  - `SiteMembershipRequest`
- Suggested columns:
  - `id`
  - `site_id`
  - `username`
  - `requested_role`
  - `message`
  - `status`
  - `requested_at`
  - `decision_by`
  - `decision_at`
  - `decision_comment`
- Status values:
  - `PENDING`
  - `APPROVED`
  - `REJECTED`
  - `WITHDRAWN`

#### 2. Use `074-create-site-membership-requests.xml`
- Include:
  - table creation
  - indexes on `(site_id, status)` and `(username, status)`
  - one-time backfill from `users.preferences -> siteMembershipRequests`
- Keep the one-release compatibility reader documented earlier, but switch service logic to:
  - DB first
  - fallback to legacy preference payload only during the transition release

#### 3. Consolidate request handling in `SiteMembershipService`
- Move request lifecycle to one service:
  - create
  - approve
  - reject
  - withdraw
  - list by site
  - list by user
- `PeopleController` should stop directly mutating preference JSON for request moderation and delegate to the service instead.

#### 4. Add site-role-based collaboration authorization
- Introduce explicit helpers such as:
  - `SecurityService.hasSiteRole(siteId, username, role)`
  - `SecurityService.canManageSiteMembers(siteId, username)`
  - `SecurityService.canModerateSiteRequests(siteId, username)`
- Initial rule set:
  - `ROLE_ADMIN` can manage all sites
  - `SiteMemberRole.MANAGER` can moderate requests and manage members for that site
- Do not remove the generic node creator fallback globally in `P1`.
- Instead, stop using generic owner semantics for site request and membership moderation paths.

#### 5. Align `SiteController` and `PeopleController`
- `SiteController` remains the primary site-membership API.
- `PeopleController` becomes a compatibility/read surface and should delegate to service-level membership request operations.
- No endpoint should need to scan all `User.preferences` after `PR-8`.

### Target Files
- `ecm-core/src/main/java/com/ecm/core/service/SiteMembershipService.java`
- `ecm-core/src/main/java/com/ecm/core/service/SecurityService.java`
- `ecm-core/src/main/java/com/ecm/core/controller/SiteController.java`
- `ecm-core/src/main/java/com/ecm/core/controller/PeopleController.java`
- `ecm-core/src/main/java/com/ecm/core/repository/SiteMemberRepository.java`
- new `ecm-core/src/main/java/com/ecm/core/entity/SiteMembershipRequest.java`
- new `ecm-core/src/main/java/com/ecm/core/repository/SiteMembershipRequestRepository.java`
- `ecm-core/src/main/resources/db/changelog/changes/074-create-site-membership-requests.xml`

### Non-Goals
- No external invitation workflow yet.
- No email invitation tokens yet.
- No global ACL model rewrite in this PR.

### PR-8 Merge Gate
- Site membership requests no longer require scanning all users.
- Moderation and member-management decisions resolve through site roles or admin privileges.
- Legacy JSONB request payload is read only for the temporary compatibility window.
- Status: implemented on 2026-04-14

## Parallel Ownership Recommendation

### Worker A
- `PR-6`
- Owns lifecycle publisher, event plumbing, and service conversion.

### Worker B
- `PR-7`
- Owns content model validation service, repository helpers, controller error behavior.

### Worker C
- `PR-8`
- Owns site membership request persistence, backfill, compatibility reader, site-role checks.

## Recommended Command Set

```bash
./ecm-core/mvnw -B -Dstyle.color=never test -Dtest=RepositoryLifecycle*Test,ContentModel*Test,SiteMembership*Test
./ecm-core/mvnw -B -Dstyle.color=never test
```

## P1 Exit Gate
- New repository write paths can integrate through the shared lifecycle publisher instead of hand-wiring rules and index calls.
- Invalid content model mutations are rejected before persistence.
- Site membership requests and moderation no longer depend on JSONB preference scans.
