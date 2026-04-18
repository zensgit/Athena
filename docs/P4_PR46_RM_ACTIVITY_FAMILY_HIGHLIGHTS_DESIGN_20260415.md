# PR-46 RM Activity Family Highlights Design

## Goal

Add a deeper RM analytics slice that compares the current RM activity window with the previous window by family, so operators can see which family is rising or falling before drilling into the existing `Records Audit` evidence table.

## Scope

Backend:

- add `GET /api/v1/records/activity-family-highlights`
- compare current and previous closed windows by RM activity family
- reuse the existing RM family model:
  - `DECLARED`
  - `UNDECLARED`
  - `CATEGORY_ASSIGNED`
  - `GOVERNANCE_CHANGE`
  - `OTHER`

Frontend:

- add `RM Activity Family Highlights` card to the RM admin page
- keep load isolated from the rest of the RM dashboard
- drill from each family row into the existing `Records Audit` table for:
  - current window
  - previous window

## Backend Contract

`GET /api/v1/records/activity-family-highlights?windowDays=7`

Parameters:

- `windowDays`
  - default: `7`
  - clamp: `2..30`

Window semantics:

- current window:
  - start-of-day of the oldest included day
  - through `23:59:59` of today
- previous window:
  - immediately preceding closed window of the same size

Response shape:

- `windowDays`
- `currentWindow`
  - `fromDay`
  - `toDay`
- `previousWindow`
  - `fromDay`
  - `toDay`
- `families`

Each family row contains:

- `family`
- `currentCount`
- `previousCount`
- `delta`
- `lastEventTime`

Ordering:

1. `max(currentCount, previousCount)` descending
2. `currentCount` descending
3. family rank:
   - `DECLARED`
   - `UNDECLARED`
   - `CATEGORY_ASSIGNED`
   - `GOVERNANCE_CHANGE`
   - `OTHER`

## Frontend Behavior

The RM page adds an `RM Activity Family Highlights` card that:

- shows current and previous window labels
- renders one row per active family
- shows:
  - family label
  - current count
  - previous count
  - signed delta vs previous window
  - last event time
- provides:
  - `Review current audit`
  - `Review previous audit`

Drilldown behavior:

- reuses the existing `Records Audit` banner and table
- pre-fills:
  - `family`
  - `from`
  - `to`
- keeps `username` and `eventType` empty
- uses current or previous window bounds depending on the clicked action

## Files

Backend:

- `ecm-core/src/main/java/com/ecm/core/controller/RecordsManagementController.java`
- `ecm-core/src/main/java/com/ecm/core/service/RecordsManagementService.java`
- `ecm-core/src/test/java/com/ecm/core/controller/RecordsManagementControllerTest.java`
- `ecm-core/src/test/java/com/ecm/core/service/RecordsManagementServiceTest.java`

Frontend:

- `ecm-frontend/src/types/index.ts`
- `ecm-frontend/src/services/recordsManagementService.ts`
- `ecm-frontend/src/services/recordsManagementService.test.ts`
- `ecm-frontend/src/pages/RecordsManagementPage.tsx`
- `ecm-frontend/src/pages/RecordsManagementPage.test.tsx`

Documentation:

- `docs/P4_PR46_RM_ACTIVITY_FAMILY_HIGHLIGHTS_DESIGN_20260415.md`
- `docs/P4_PR46_RM_ACTIVITY_FAMILY_HIGHLIGHTS_VERIFICATION_20260415.md`
- `docs/P4_RECORDS_MANAGEMENT_EXECUTION_PLAN_20260414.md`
- `docs/P4_RECORDS_MANAGEMENT_ACCEPTANCE_20260414.md`

## Deferred

- family highlight sparklines or mini-trends
- previous-window percentage deltas
- family highlight export/report surfaces
