# P4 PR-47 Claude Backend Task Brief

## Goal

Define the next backend-only task that is isolated enough for `Claude` to implement while keeping local review/fixup safe and cheap.

## Recommendation

Do **not** hand `Claude` another RM page micro-slice.

The next suitable backend task should be a **new analytics data surface**, not more UI polish.

Recommended candidate:

- `PR-47: RM Activity Family Export / Report API`

## Why This Is a Good Claude Task

It is well-bounded:

- backend-only
- additive
- no repository-kernel coupling
- no ACL/index correctness changes
- no storage lifecycle changes

It also builds naturally on `PR-41 ~ PR-46`:

- it reuses existing audit-derived RM analytics
- it does not require redesigning the current page
- it can be reviewed mostly for query correctness and contract stability

## Proposed Scope

Backend only:

- add `GET /api/v1/records/activity-family-report`
- accept:
  - `from`
  - `to`
  - optional `format=csv|json`
- return/export:
  - family totals
  - current vs previous deltas if both windows are derivable
  - top event types per family
  - optional top contributors per family

Keep it explicitly out of scope:

- no new tables
- no scheduled materialization
- no frontend page work
- no change to repository semantics

## Files Claude Should Own

Likely backend write set:

- `ecm-core/src/main/java/com/ecm/core/controller/RecordsManagementController.java`
- `ecm-core/src/main/java/com/ecm/core/service/RecordsManagementService.java`
- `ecm-core/src/main/java/com/ecm/core/repository/AuditLogRepository.java`
- `ecm-core/src/test/java/com/ecm/core/controller/RecordsManagementControllerTest.java`
- `ecm-core/src/test/java/com/ecm/core/service/RecordsManagementServiceTest.java`

## Review Focus For Local Reviewer

When this is handed back for review, the main checks should be:

- date-range semantics are explicit and closed-interval
- no second evidence model is created
- report/export contract is stable and additive
- tests cover:
  - empty result
  - mixed-family result
  - `OTHER` family inclusion
  - range clamping / validation

## Preconditions

Before assigning this to `Claude Code CLI` on this machine:

- complete `claude /login`
- verify a simple read-only print command works

Without that, use local subagents or local implementation instead.
