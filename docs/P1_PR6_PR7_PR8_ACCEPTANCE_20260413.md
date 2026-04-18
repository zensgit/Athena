# P1 PR-6 / PR-7 / PR-8 Acceptance Plan

## Date
- 2026-04-13

## Status
- `PR-6` passed
- `PR-7` passed
- `PR-8` passed

## Scope
- Validate `P1` kernel hardening after `P0A` and `P0B` closure.
- Cover:
  - `PR-6` minimal lifecycle hook pipeline
  - `PR-7` runtime content model governance validation
  - `PR-8` site membership persistence and authority hardening

## Test Execution Baseline
- Use [ecm-core/mvnw](/Users/chouhua/Downloads/Github/Athena/ecm-core/mvnw:1) for all backend validation.
- Run targeted suites before full backend tests.

## PR-6 Acceptance

### Unit Coverage
- lifecycle publisher emits the expected `RepositoryLifecycleAction` for:
  - create
  - update
  - move
  - delete
  - permissions changed
  - check-in
- lifecycle publisher bridges to legacy domain events exactly once where compatibility is required
- rule-dispatch listener maps lifecycle actions to the expected `TriggerType`

### Integration Coverage
- `NodeService.updateNode(...)` produces one lifecycle dispatch and one rule execution path
- `VersionService.createVersion(...)` no longer triggers rules directly from the service body
- `BulkMetadataService` does not emit duplicate update flows
- permission change still reindexes search through the shared lifecycle/event path

### Manual Acceptance
1. Create a document and update metadata.
2. Confirm audit, index, and rule behavior still occur once.
3. Check in a working copy.
4. Confirm version creation, audit, and index update still complete without duplicate side effects.

### Suggested Test Classes
- `RepositoryLifecyclePublisherTest`
- `RepositoryLifecycleRuleDispatchTest`
- `NodeServiceLifecycleIntegrationTest`
- `VersionServiceLifecycleIntegrationTest`

## PR-7 Acceptance

### Unit Coverage
- circular type inheritance is rejected
- circular aspect inheritance is rejected
- missing parent type/aspect is rejected
- duplicate namespace or prefix update is rejected
- deleting an in-use property returns validation failure

### Integration Coverage
- activating an invalid model returns `400`
- deleting a live in-use type returns `400`
- deleting a live in-use aspect returns `400`
- deleting an unused draft definition succeeds
- workflow/rule reference validation blocks destructive change when a live automation rule still references the removed dependency

### Manual Acceptance
1. Create a draft model with invalid parent chain and attempt activation.
2. Attach a type/aspect to a live node and attempt delete.
3. Confirm API returns explicit validation error text instead of silent delete or generic `500`.

### Suggested Test Classes
- `RuntimeModelValidationServiceTest`
- `ContentModelServiceValidationTest`
- `ContentModelControllerValidationTest`

## PR-8 Acceptance

### Unit Coverage
- creating a request inserts one `SiteMembershipRequest` row
- approving a request creates membership and updates request status
- rejecting a request stores moderator metadata without creating membership
- withdrawing a request only removes or transitions the caller's own pending row
- site-role checks accept `ROLE_ADMIN` and site `MANAGER`, reject plain `CONSUMER`

### Integration Coverage
- listing site requests reads from first-class persistence without scanning all users
- listing user requests returns DB-backed request rows
- `074` migration backfills legacy JSONB site requests into the new table
- compatibility reader still serves legacy rows during the transition release
- member moderation no longer depends on direct `User.preferences` mutation inside `PeopleController`

### Manual Acceptance
1. User requests access to a moderated site.
2. Site manager approves the request.
3. User appears in site member roster with the requested role.
4. Same site request no longer requires a full-user preference scan.

### Suggested Test Classes
- `SiteMembershipRequestRepositoryTest`
- `SiteMembershipServicePersistenceTest`
- `SiteMembershipServiceAuthorizationTest`
- `PeopleControllerSiteMembershipCompatibilityTest`
- `SiteMembershipMigrationTest`

## Migration Acceptance

### ChangeSet `074`
- fresh install succeeds
- upgrade on non-empty database succeeds
- backfill count matches legacy request payload count
- application starts with `ecm.site.membership.persistence.enabled=true`

### Rollback Expectations
- rollback must be documented
- if compatibility reader remains enabled, rollback procedure must describe how DB rows and legacy JSONB payload interact

## Command Sequence

```bash
./ecm-core/mvnw -B -Dstyle.color=never test -Dtest=RepositoryLifecycle*Test
./ecm-core/mvnw -B -Dstyle.color=never test -Dtest=RuntimeModelValidationServiceTest,ContentModel*Test
./ecm-core/mvnw -B -Dstyle.color=never test -Dtest=SiteMembership*Test,PeopleController*SiteMembership*Test
./ecm-core/mvnw -B -Dstyle.color=never test
```

## Merge Decision Rules

### PR-6
- merge only if lifecycle integration tests show no duplicate rule/index paths
- Status: passed on 2026-04-13

### PR-7
- merge only if destructive invalid model changes fail before persistence
- Status: passed on 2026-04-13

### PR-8
- merge only if `074` migration verification passes and site request flows no longer depend on JSONB scans
- Status: automated verification passed on 2026-04-14

## P1 Gate Close Criteria
- `PR-6`, `PR-7`, and `PR-8` all merged
- targeted suites green
- full backend suite green
- manual site-membership smoke passes on migrated data
- no remaining write path requires ad hoc rule/index wiring for the `P1` lifecycle surface

## Current State
- targeted suites green
- full backend suite green
- migrated-data manual smoke still recommended before production rollout
