# PR-45 RM Activity Family Mix Design

## Goal

Add a small but deeper RM analytics slice that summarizes recent RM activity by event family, so operators can see the declared/undeclared/category/governance/other mix before drilling into the existing `Records Audit` evidence table.

## Scope

Backend:

- add `GET /api/v1/records/activity-families`
- aggregate existing `RM_%` audit data by family over a recent closed window
- extend `listAudit(..., family=OTHER, ...)` so family drilldown stays complete

Frontend:

- add `RM Activity Family Mix` card to the RM admin page
- keep load isolated from the rest of the RM dashboard
- drill from family rows into the existing `Records Audit` table using `family + from + to`

## Backend Contract

`GET /api/v1/records/activity-families?days=28`

Parameters:

- `days`
  - default: `28`
  - clamp: `7..90`

Window semantics:

- start-of-day of the oldest included day
- through `23:59:59` of today

Response shape:

- `days`
- `totalCount`
- `families`

Each row contains:

- `family`
- `count`
- `lastEventTime`

Ordering:

1. `count` descending
2. family rank:
   - `DECLARED`
   - `UNDECLARED`
   - `CATEGORY_ASSIGNED`
   - `GOVERNANCE_CHANGE`
   - `OTHER`

## Audit Family Completeness

Before this slice, analytics already classified `OTHER`, but `Records Audit` family filtering only supported the four named families. `PR-45` closes that gap.

Changes:

- `RmEventFamily` now includes `OTHER`
- `listAudit(..., family=OTHER, ...)` routes to a complementary RM audit query
- frontend audit family filter now exposes `Other`

This keeps `RM Activity Family Mix` fully drillable instead of leaving `OTHER` as a dead-end summary bucket.

## Frontend Behavior

The RM page adds an `RM Activity Family Mix` card that:

- lists recent RM families with:
  - family label
  - count
  - share of total
  - last event timestamp
- provides `Review family audit`

Drilldown behavior:

- computes the same recent closed window semantics in local date terms
- pre-fills:
  - `family`
  - `from`
  - `to`
- keeps `username` and `eventType` empty
- reuses the existing `Records Audit` banner and table

## Files

Backend:

- `ecm-core/src/main/java/com/ecm/core/controller/RecordsManagementController.java`
- `ecm-core/src/main/java/com/ecm/core/service/RecordsManagementService.java`
- `ecm-core/src/main/java/com/ecm/core/repository/AuditLogRepository.java`
- `ecm-core/src/test/java/com/ecm/core/controller/RecordsManagementControllerTest.java`
- `ecm-core/src/test/java/com/ecm/core/service/RecordsManagementServiceTest.java`

Frontend:

- `ecm-frontend/src/types/index.ts`
- `ecm-frontend/src/services/recordsManagementService.ts`
- `ecm-frontend/src/services/recordsManagementService.test.ts`
- `ecm-frontend/src/pages/RecordsManagementPage.tsx`
- `ecm-frontend/src/pages/RecordsManagementPage.test.tsx`

Documentation:

- `docs/P4_PR45_RM_ACTIVITY_FAMILY_MIX_DESIGN_20260415.md`
- `docs/P4_PR45_RM_ACTIVITY_FAMILY_MIX_VERIFICATION_20260415.md`
- `docs/P4_RECORDS_MANAGEMENT_EXECUTION_PLAN_20260414.md`
- `docs/P4_RECORDS_MANAGEMENT_ACCEPTANCE_20260414.md`

## Deferred

- family-mix trend deltas across previous windows
- backend-provided percentage/share values
- deeper family/event cross-tabs or charting
