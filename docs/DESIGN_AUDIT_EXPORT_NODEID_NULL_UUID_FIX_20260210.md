# Design: Fix Audit Export Optional `nodeId` Filter (PostgreSQL UUID NULL) (2026-02-10)

## Context
The admin audit export endpoint supports optional filters:

- username
- eventType
- category
- nodeId (UUID)
- from/to time range

The UI uses this endpoint in the "Audit export" flow and expects a CSV download.

## Problem
When `nodeId` is omitted (NULL), PostgreSQL can fail to infer the SQL type for a NULL UUID parameter when the JPQL uses the common pattern:

`(:nodeId IS NULL OR a.nodeId = :nodeId)`

Observed failure:

- HTTP 500 from `GET /api/v1/analytics/audit/export`
- PostgreSQL error `SQLState 42P18` ("could not determine data type of parameter $N")

This breaks the UI audit export flow and the Playwright E2E gate.

## Goals
- Make audit export robust when `nodeId` is null.
- Keep API behavior unchanged for callers.
- Keep queries readable and predictable.

## Non-Goals
- Change audit schema or introduce new DB indexes in this change set.

## Design
### Query splitting instead of `OR :param IS NULL`
Introduce repository query variants that omit the `nodeId` predicate entirely:

- `findByFiltersForExportNoNodeId(...)`
- `findByFiltersForExportAndCategoryNoNodeId(...)`
- `findByFiltersAndCategoryNoNodeId(...)` (paged search path)

Route in `AnalyticsService`:

- if `nodeId == null`: call the `NoNodeId` variant
- else: call the existing query that includes `a.nodeId = :nodeId`

This avoids the PostgreSQL UUID NULL type inference issue without requiring casts or database-specific JPQL.

## Implementation Notes
### Files
- `ecm-core/src/main/java/com/ecm/core/repository/AuditLogRepository.java`
- `ecm-core/src/main/java/com/ecm/core/service/AnalyticsService.java`

### Behavioral Summary
- `GET /api/v1/analytics/audit/export`: no longer fails with 500 when `nodeId` is omitted.
- `GET /api/v1/analytics/audit/search`: same fix path to prevent the same NULL UUID problem on the paged search query.

## Validation
See `docs/VERIFICATION_PHASE1_P0_20260210.md`.

