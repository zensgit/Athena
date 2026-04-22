# RM Scheduled Report Preset Delivery — Capability Reference

## Date
2026-04-22

## What this document is

A single entry point to the RM scheduled-delivery capability delivered
across PR-95 → PR-110. New reviewers and future slice authors should
start here instead of reading sixteen individual PR docs in order.

This document is **not** a design doc — each PR doc remains the
canonical record for the decisions made in that slice. This is a
rollup: what shipped, where it lives, and how the pieces fit.

## One-sentence summary

Admins save an RM activity report configuration as a named preset,
attach a cron schedule + delivery folder, and the scheduler renders
the report to CSV and uploads it as a regular document into the
chosen folder — with an execution ledger, owner-scoped telemetry,
and UI drilldown from a health card.

## Capability map

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         Operator (admin) flow                           │
│                                                                          │
│   RM page → Save preset → Schedule dialog → Pick folder in tree         │
│                              │                                           │
│                              ▼                                           │
│            Enter cron + timezone → Save → Deliver now / wait for cron   │
│                              │                                           │
│                              ▼                                           │
│    Health card + filter chips + per-row status on the preset table     │
│                              │                                           │
│                              ▼                                           │
│        Preset Delivery Ledger (filter, paginate, CSV export,           │
│         open delivered file / target folder in browse)                 │
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│                               Runtime                                    │
│                                                                          │
│   @Scheduled(cron = "0 */5 * * * *")                                     │
│   runScheduledDeliveries                                                 │
│     ↓                                                                    │
│   scan due presets (owner-agnostic, by next_run_at <= now)               │
│     ↓                                                                    │
│   for each due preset:                                                   │
│     1. claimScheduledRun   — atomic CAS on next_run_at                   │
│     2. reload                                                            │
│     3. render CSV (dispatch by Kind)                                     │
│     4. upload as document into deliveryFolderId                          │
│     5. persist execution ledger row (SUCCESS | FAILED)                   │
│     6. audit event                                                       │
└─────────────────────────────────────────────────────────────────────────┘
```

## Slice index

Each link goes to the canonical dev/verification doc for that slice.

| Slice | Layer | Purpose |
|-------|-------|---------|
| PR-95 | Backend | Scheduled delivery backend (entity, migration, scheduler, delivery service, ledger) |
| PR-96 | Frontend service | Typed client methods + kind guard |
| PR-97 | Frontend component | `ScheduleReportPresetDialog` — load/save/deliver-now/history UI |
| PR-98 | Frontend page wiring | Schedule action on preset row + mount dialog |
| PR-99 | Frontend polish | UI hardening + dialog auto-refresh |
| PR-100 | E2E | Mocked Playwright spec for the schedule flow |
| PR-101 | E2E | Full-stack Playwright spec (live backend) |
| PR-102 | Frontend polish | Dialog ledger polish |
| PR-103 | Backend | Filter + export API for the preset execution ledger |
| PR-104 | Frontend page | Preset Delivery Ledger card (filter, paginate, export) |
| PR-105 | Frontend polish | Ledger filter chips + zero-match empty state |
| PR-106 | Frontend | Delivery folder tree picker replaces UUID text input |
| PR-107 | Backend | `GET /report-presets/telemetry` endpoint |
| PR-108 | Frontend | Scheduled Delivery Health card |
| PR-109 | Full-stack | Schedule metadata on list + drilldown + CAS claim query |
| PR-110 | Docs | Claim-before-upload hardening writeup |
| PR-111 | Backend + frontend | Summary preset (HIGHLIGHTS/MIX) CSV + schedule support |
| PR-112 | E2E | Mocked Playwright spec for summary preset schedule |
| PR-113 | E2E | Full-stack Playwright spec for summary preset schedule |
| PR-114 | Full-stack | Preset delivery surface refresh + ledger consistency |
| PR-115 | E2E | Mocked ledger operator polish spec |
| PR-116 | E2E | Full-stack ledger spec |

## Public API surface

All endpoints live under `/api/v1/records/report-presets`, admin-gated
at the controller, owner-scoped at the service.

| Verb | Path | Purpose |
|------|------|---------|
| GET | `/` | List my presets (returns schedule fields additively) |
| POST | `/` | Create preset |
| GET | `/{id}` | Get one |
| PUT | `/{id}` | Update name/description/params |
| DELETE | `/{id}` | Soft-delete |
| GET | `/telemetry` | Owner-scoped scheduled-delivery health summary |
| GET | `/{id}/schedule` | Current schedule + last execution |
| PUT | `/{id}/schedule` | Configure cron/timezone/folder |
| POST | `/{id}/deliver` | Manually deliver now |
| GET | `/{id}/executions?limit=N` | Recent executions for one preset |
| GET | `/executions` | Paged ledger across all owned presets |
| GET | `/executions/export` | CSV export of the filtered ledger |
| POST | `/{id}/execute` | (PR-92) Re-run preset and return the report body |

## Data model

### `rm_report_presets` (entity `RmReportPreset`)
| Column | Type | Notes |
|--------|------|-------|
| id | uuid | PK |
| owner | varchar | Athena username |
| name | varchar | unique per owner |
| description | text | |
| kind | enum | 7 values (see RmReportPreset.Kind) |
| params | jsonb | saved knobs per kind |
| schedule_enabled | boolean | defaults false |
| cron_expression | varchar | null when disabled |
| schedule_timezone | varchar | IANA; defaults UTC |
| delivery_folder_id | uuid | target folder for rendered CSV |
| next_run_at | timestamp | CAS-protected via `claimScheduledRun` |
| last_run_at | timestamp | |
| deleted | boolean | soft-delete |
| entity_version | bigint | optimistic lock |

### `rm_report_preset_executions` (entity `RmReportPresetExecution`)
| Column | Type | Notes |
|--------|------|-------|
| id | uuid | PK |
| preset_id | uuid | FK → `rm_report_presets.id` |
| owner | varchar | denormalized for ledger queries |
| trigger_type | enum | `MANUAL` \| `SCHEDULED` |
| status | enum | `SUCCESS` \| `FAILED` |
| filename | varchar | CSV filename uploaded |
| target_folder_id | uuid | |
| document_id | uuid | if delivered |
| message | varchar(2000) | success marker or failure reason |
| started_at | timestamp | |
| finished_at | timestamp | |
| duration_ms | bigint | |

## Schedulable kinds (CSV-deliverable)

All seven `Kind` enum values support scheduled CSV delivery (per
PR-111):

- `ACTIVITY_FAMILY_REPORT`
- `ACTIVITY_FAMILY_HIGHLIGHTS` — renders as a rolling-window family
  report using the preset's `windowDays` param
- `ACTIVITY_FAMILY_MIX` — renders as a rolling-window family report
  using the preset's `days` param
- `ACTIVITY_EVENT_TYPE_REPORT`
- `ACTIVITY_CONTRIBUTOR_REPORT`
- `ACTIVITY_CONTRIBUTOR_FAMILY_REPORT`
- `ACTIVITY_CONTRIBUTOR_EVENT_TYPE_REPORT`

HIGHLIGHTS and MIX share the family-report CSV shape — the scheduler
resolves their rolling windows to a concrete `from/to` range before
rendering, so the delivered CSV file always has the same columns.

The frontend `supportsReportPresetCsvDelivery` type guard and the
backend `supportsScheduledDelivery` predicate are kept in sync over
this same set. Any future expansion / restriction must update both
sides together.

## Concurrency guarantees

- `claimScheduledRun` is a `@Modifying` compare-and-set that advances
  `next_run_at` only if the caller saw the expected previous value.
  Rows-affected = 1 means this replica wins; 0 means another replica
  already claimed (logged at `debug`).
- `entity_version` is bumped by the claim query itself so subsequent
  JPA saves in the same transaction also go through optimistic locking.
- `runScheduledDeliveries` runs under a per-owner security context
  synthesized from the preset's `owner` column, so audit attribution
  is always correct.

## Frontend state (RecordsManagementPage)

The page fetches four streams of preset-related state on mount:

| Loader | Updates |
|--------|---------|
| `loadReportPresets` | preset list + per-row schedule fields |
| `loadPresetExecutionLedger` | delivery ledger page |
| `loadScheduledDeliveryTelemetry` | health card counts |
| `loadRecords` etc. | the rest of the page |

Health card drilldown writes to the same filter state the chips
above the preset table read, so clicking "Due now: 2" scrolls down
and filters the table without a second fetch.

## Acceptance matrix

The capability is covered by:

- Backend unit tests (32 passing on this capability's classes)
  - `RmReportPresetServiceTest`
  - `RmReportPresetDeliveryServiceTest`
  - `RmReportPresetControllerTest`
  - `RecordsManagementControllerSecurityTest`
- Frontend unit tests (131 passing on the relevant modules)
  - `recordsManagementService.test.ts`
  - `ScheduleReportPresetDialog.test.tsx`
  - `RecordsManagementPage.test.tsx`
- Mocked e2e: `rm-report-preset-schedule.mock.spec.ts`
- Full-stack e2e: `rm-report-preset-schedule.spec.ts`

Phase 5 Mocked gate remains pre-existing cancelled (separate
investigation tracked in session memory).

## Explicit non-goals (still deferred)

These have been flagged repeatedly across PR-97..110 and remain out
of scope for this capability:

- **Email delivery channel** — would add an SMTP sender as a second
  delivery surface. M-sized slice; depends on Phase-1 plan #5.
- **Admin delegation / cross-owner telemetry** — would need a
  separate endpoint with an explicit authorization model.
- **SLO thresholds / alerting** — would wire the telemetry numbers
  to an alert hook (email, webhook, etc.).

## Natural next step

This capability is complete. The next slice in this area should be
a direction change, not another polish layer. Candidates:

1. Email delivery channel (extends the capability)
2. A new capability from the Phase-1 plan (`docs/.../drifting-tinkering-truffle.md`):
   #5 Email SMTP, #3 Scheduled User Actions, #7 Site Invitations
