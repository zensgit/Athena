# PR-42: RM Activity Contributors — Design

## Objective

Extend the RM analytics surface from pure time-based activity into actor-based activity. This PR adds an audit-backed contributor aggregation endpoint and a thin RM admin-page card that lets operators jump from a contributor row into the existing `Records Audit` table with a prefilled `username + date range`.

## Scope

- **In scope**
  - new backend analytics endpoint:
    - `GET /api/v1/records/activity-contributors?days=28&limit=5`
  - contributor aggregation using existing `RM_%` audit data only
  - frontend `RM Activity Contributors` card
  - contributor drilldown into the existing `Records Audit` table
  - docs and tests
- **Out of scope**
  - new tables or migrations
  - new audit/evidence surface
  - per-event-family contributor drilldown
  - cross-page navigation or a separate contributor page

## Backend Design

### Endpoint

```http
GET /api/v1/records/activity-contributors?days=28&limit=5
```

### Parameters

| Parameter | Default | Clamp | Description |
|-----------|---------|-------|-------------|
| `days` | `28` | `7..90` | Recent lookback window |
| `limit` | `5` | `1..50` | Maximum contributors returned |

### Window Semantics

- recent closed window
- starts at `00:00:00` of the oldest included day
- ends at `23:59:59` of today

### Response Shape

```json
{
  "days": 28,
  "limit": 5,
  "contributors": [
    {
      "username": "admin",
      "label": "admin",
      "declaredCount": 3,
      "undeclaredCount": 1,
      "categoryAssignedCount": 2,
      "governanceChangeCount": 1,
      "totalCount": 7,
      "lastEventTime": "2026-04-14T10:30:00"
    }
  ]
}
```

### Event Classification

Reuses the same RM family mapping already used by timeline / highlights / breakdown:

- `declaredCount`
- `undeclaredCount`
- `categoryAssignedCount`
- `governanceChangeCount`

Unclassified `RM_%` events are intentionally ignored rather than inventing a fifth family.

### Label Semantics

- non-blank username -> `label = username`
- blank / null username -> `label = "(System)"`, `username = null`

### Ordering

1. `totalCount` descending
2. `label` ascending, case-insensitive

## Frontend Design

### New Card

Add `RM Activity Contributors` to the RM admin page as an isolated analytics card.

Each contributor row shows:

- display label
- family breakdown
- `lastEventTime`
- `totalCount`
- `Review contributor audit`

### Drilldown Behavior

- contributor drilldown reuses the existing `Records Audit` table
- it pre-fills:
  - `username`
  - `from`
  - `to`
- no new audit load path is introduced
- active contributor drilldown is still represented through the existing audit-drilldown banner

### Frontend Range Semantics

- frontend builds a recent range using the contributor window size
- range is generated in local date terms, not UTC-truncated `toISOString()` slicing
- backend remains authoritative for actual audit filtering

## Files

### Backend

- `ecm-core/src/main/java/com/ecm/core/controller/RecordsManagementController.java`
- `ecm-core/src/main/java/com/ecm/core/service/RecordsManagementService.java`
- `ecm-core/src/main/java/com/ecm/core/repository/AuditLogRepository.java`
- backend RM controller/service tests

### Frontend

- `ecm-frontend/src/services/recordsManagementService.ts`
- `ecm-frontend/src/services/recordsManagementService.test.ts`
- `ecm-frontend/src/pages/RecordsManagementPage.tsx`
- `ecm-frontend/src/pages/RecordsManagementPage.test.tsx`
- `ecm-frontend/src/types/index.ts`

## Compatibility

- no schema changes
- no migration
- no changes to existing analytics headings
- analytics load remains isolated; contributor failure must not block the rest of the page
