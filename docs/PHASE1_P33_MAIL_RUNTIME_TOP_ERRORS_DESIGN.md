# Phase 1 - P33 Mail Runtime Top Errors (Design)

Date: 2026-02-07

## Objective

Improve Mail Automation runtime observability by surfacing **top recurring error reasons** in the existing Runtime Health panel.

## Motivation

Current runtime metrics only expose aggregate counters (`attempts/success/errors/errorRate`) and timestamps (`lastSuccessAt/lastErrorAt`), but operators cannot quickly see *which* errors dominate recent failures.

This creates avoidable diagnosis latency when the runtime status is `DEGRADED` or `DOWN`.

## Scope

- Backend:
  - add aggregation query for recent error messages
  - include grouped top errors in `runtime-metrics` response
- Frontend:
  - render top error reasons in Runtime Health card
- Out of scope:
  - changing fetch/replay behavior
  - changing retry policy

## Backend Design

### Repository

File: `ecm-core/src/main/java/com/ecm/core/integration/mail/repository/ProcessedMailRepository.java`

- Added native aggregation query:
  - source table: `mail_processed_messages`
  - filter: `status='ERROR'` and within runtime metrics window
  - group by normalized `error_message` (blank -> `Unknown error`)
  - order by count desc, last seen desc
  - pageable limit (top N)
- Added projection:
  - `MailRuntimeErrorAggregateRow`
    - `errorMessage`
    - `totalCount`
    - `lastSeenAt`

### Service

File: `ecm-core/src/main/java/com/ecm/core/integration/mail/service/MailFetcherService.java`

- Extended `getRuntimeMetrics(...)`:
  - query top 5 grouped errors
  - compact and truncate long error text for display stability
- Added DTO record:
  - `MailRuntimeErrorStat(errorMessage, count, lastSeenAt)`
- Extended DTO:
  - `MailRuntimeMetrics(..., status, topErrors)`

## Frontend Design

### API Types

File: `ecm-frontend/src/services/mailAutomationService.ts`

- Added `MailRuntimeErrorStat`
- Extended `MailRuntimeMetrics` with optional:
  - `topErrors?: MailRuntimeErrorStat[]`

### UI

File: `ecm-frontend/src/pages/MailAutomationPage.tsx`

- Runtime Health card now renders:
  - section `Top error reasons` when `topErrors` has items
  - warning chips: `errorMessage (count)`
  - tooltip with `Last seen` timestamp

## Compatibility

- Backward compatible:
  - existing clients can ignore new `topErrors` field
- No endpoint path or request contract changes

