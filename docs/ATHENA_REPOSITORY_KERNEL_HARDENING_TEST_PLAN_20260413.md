# Athena Repository Kernel Hardening Test Plan

## Date
- 2026-04-13

## Status
- Draft

## References
- [ATHENA_REPOSITORY_KERNEL_HARDENING_DEV_AND_ACCEPTANCE_20260413.md](/Users/chouhua/Downloads/Github/Athena/docs/ATHENA_REPOSITORY_KERNEL_HARDENING_DEV_AND_ACCEPTANCE_20260413.md)
- [ATHENA_REPOSITORY_KERNEL_HARDENING_TASK_BREAKDOWN_20260413.md](/Users/chouhua/Downloads/Github/Athena/docs/ATHENA_REPOSITORY_KERNEL_HARDENING_TASK_BREAKDOWN_20260413.md)

## Goal
- Define the minimum test inventory required to ship P0 and P1 kernel hardening safely.
- Tie every acceptance criterion to concrete automated coverage.

## Test Layers
- Unit: service-level logic, deterministic edge cases, no container dependency unless needed.
- Integration: Spring Boot + database + search stack where transaction or persistence correctness matters.
- Migration: Liquibase upgrade verification and backfill correctness.
- Acceptance: cross-user or end-to-end flows that assert user-visible behavior.

## Coverage Matrix

| Area | Unit | Integration | Migration | Acceptance |
| --- | --- | --- | --- | --- |
| Content reference ledger | Required | Required | Required | Optional |
| Binary orphan cleanup | Required | Required | Optional | Optional |
| Check-in version creation | Required | Required | No | Required |
| Subtree move path consistency | Optional | Required | No | Required |
| ACL delta indexing | Optional | Required | No | Required |
| Model validation | Required | Required | Optional | Optional |
| Site membership persistence | Required | Required | Required | Required |

## P0A Test Cases

### Content Reference Ledger

#### Unit Cases
- create reference for document owner
- create reference for version owner
- duplicate owner registration is idempotent or explicitly rejected
- detach reference marks ownership inactive without touching unrelated owners
- orphan detection returns true only when no active references remain

#### Integration Cases
- upload content, create document, create version, verify 2 reference rows
- delete non-current version, verify binary still exists because document still references it
- create 2 versions sharing one content id, delete one version, verify binary still exists
- delete final remaining owner, verify orphan candidate is created

#### Migration Cases
- migrate a dataset with documents and versions
- verify every non-null `content_id` became one ledger row
- verify rerun behavior is safe

### Check-In Creates Version

#### Unit Cases
- check-in delegates to version creation when working copy content changes
- failed version creation aborts original update
- keep-checked-out path only creates new working copy after successful version creation

#### Integration Cases
- checkout document, modify working copy, check in, verify version count increments
- check in with metadata-only change, verify history entry exists if required by design
- concurrent check-in by non-owner fails
- rollback simulation leaves working copy undeleted and original untouched

#### Acceptance Cases
- user checks out, edits, checks in, then opens version history and sees a new entry
- revert from the new check-in version succeeds

## P0B Test Cases

### Recursive Path Consistency

#### Integration Cases
- create `root/A/B/C/doc`
- move `A` under `root/X`
- assert DB paths:
  - `A -> /root/X/A`
  - `B -> /root/X/A/B`
  - `C -> /root/X/A/B/C`
  - `doc -> /root/X/A/B/C/doc`
- assert no descendant retains the old prefix

#### Acceptance Cases
- folder tree shows moved subtree in new location only
- search path display uses the new path only

### ACL Delta Indexing

#### Integration Cases
- user `alice` gets `READ`, commit transaction, search returns node
- revoke `READ` from `alice`, commit transaction, search returns nothing
- enable inherited ACL on parent, verify child search visibility updates
- disable inherited ACL on parent, verify child visibility updates

#### Acceptance Cases
- admin grants access, second user can search document
- admin revokes access, second user can no longer search document after commit

## P1 Test Cases

### Minimal Lifecycle Hook Contract

#### Unit Cases
- lifecycle publisher emits correct event types for create/update/move/delete/check-in/permission change
- downstream consumers can subscribe without duplicate manual invocations

#### Integration Cases
- node update triggers exactly one rule/index/audit flow via shared lifecycle path
- permission change triggers shared lifecycle event and search reindex

### Model Validation

#### Unit Cases
- circular type inheritance is rejected
- duplicate namespace is rejected
- property delete fails when property is still referenced

#### Integration Cases
- activating invalid model returns explicit validation error
- deleting active in-use type fails before persistence
- deleting unused draft model succeeds

### Site Membership Persistence

#### Unit Cases
- create request row
- approve request creates membership
- withdraw removes only caller's pending request

#### Integration Cases
- list site requests no longer scans user preferences
- query by site and status uses first-class persistence
- migration backfills preference payload requests into request table
- compatibility reader still serves legacy JSONB payload during the transition release only

#### Acceptance Cases
- user requests site access
- site admin reviews and approves
- user appears in site member list

## Release Gate Test Minimums

### Gate P0A
- All unit and integration cases for content ledger and check-in are green.
- One migration verification run is green.

### Gate P0B
- Move subtree integration suite is green.
- ACL grant/revoke search suite is green against committed transactions.

### Gate P1
- Lifecycle hook integration tests are green.
- Model validation negative cases are green.
- Site membership persistence acceptance path is green.

## Non-Goals
- Full UI redesign coverage.
- Broad protocol regression for CMIS/WebDAV/WOPI in this phase.
- Performance benchmarking beyond targeted regression checks.

## Recommended Automation Order
1. Unit tests for content reference ledger
2. Integration tests for binary lifecycle
3. Integration tests for check-in versioning
4. Integration tests for subtree move consistency
5. Integration tests for ACL delta indexing
6. Governance and site persistence tests
