# P5 PR-108 Scheduled Delivery Health Card — Dev & Verification

## Date
2026-04-21

## Scope

Frontend consumption of the PR-107 telemetry endpoint. Renders a
"Scheduled Delivery Health" card on `RecordsManagementPage` showing
the six-field health summary at a glance. Purely additive.

## Why now

PR-107's "Follow-up slices" section listed frontend consumption as
the natural next slice. No Codex auto-deliveries were pending this
turn, so I picked this scoped follow-up.

## Included

### Types (`ecm-frontend/src/types/index.ts`)
```typescript
export interface RmScheduledDeliveryTelemetry {
  scheduleEnabledCount: number;
  duePresetCount: number;
  last24hSuccessCount: number;
  last24hFailedCount: number;
  lastExecutionAt?: string | null;
  generatedAt: string;
}
```
Mirrors the backend DTO 1:1.

### Service method
```typescript
async getScheduledDeliveryTelemetry(): Promise<RmScheduledDeliveryTelemetry>
→ GET /records/report-presets/telemetry
```

### Page card
A new `<Card>` titled "Scheduled Delivery Health" placed directly
above the Preset Delivery Ledger (logical ordering: summary first,
details second). Renders a row of chips:
- Scheduled presets (outlined, primary color)
- Due now (outlined, warning when > 0)
- Last 24h success (outlined, success)
- Last 24h failed (outlined, error when > 0)
- Last delivery (outlined, only if present)

### Loader
`loadScheduledDeliveryTelemetry` is a `useCallback` that matches the
pattern of `loadReportPresets`. Failures surface as inline text
under the card instead of a toast — this is background telemetry,
not a user-initiated action.

## Design Decisions

1. **Chips, not a table or big numbers.** Keeps the card compact —
   six fields would dominate the page if rendered as large KPI
   tiles. Chips group naturally next to the ledger filters below.
2. **Color hint only when actionable.** `duePresetCount > 0` gets
   warning color (not red), `last24hFailedCount > 0` gets error.
   Zero values stay neutral, avoiding "always red" noise.
3. **No polling.** The card loads once on mount. Refreshing would
   require either a poll interval or a bus-event hook. Both are
   out of scope for a single-slice add.
4. **No toast on error.** Telemetry errors are surfaced inline so a
   transient backend hiccup doesn't spam notifications.
5. **`Last delivery` only shown when present.** Brand-new presets
   with no execution yet shouldn't render "Last delivery: —".
6. **Placed before the ledger.** Summary → detail reading order.
   Matches how the rest of the page is structured (health cards
   near the top).

## Verification

```
cd ecm-frontend && CI=true npm test -- \
  --testPathPattern='RecordsManagementPage.test.tsx|ScheduleReportPresetDialog.test.tsx|recordsManagementService.test.ts' \
  --watchAll=false
→ Tests: 130 passed, 130 total
```

Breakdown (+2 over PR-106's 128):
- +1 `recordsManagementService` test: GET telemetry URL wiring
- +1 `RecordsManagementPage` test: card renders all five chips

```
npx -p typescript@5.4.5 tsc --noEmit  → clean
npm run lint                          → clean
```

## Files Changed

| File | Kind |
|------|------|
| `ecm-frontend/src/types/index.ts` | +10 lines (interface) |
| `ecm-frontend/src/services/recordsManagementService.ts` | +5 lines (method + import) |
| `ecm-frontend/src/services/recordsManagementService.test.ts` | +14 lines (test) |
| `ecm-frontend/src/pages/RecordsManagementPage.tsx` | +80 lines (state, loader, useEffect, card) |
| `ecm-frontend/src/pages/RecordsManagementPage.test.tsx` | +19 lines (mock + test) |

No backend changes. No migration.

## Expected CI Outcome

| Job | Expected |
|-----|----------|
| Backend Verify | ✅ unchanged (no backend change) |
| **Frontend Build & Test** | **✅ 130 unit tests green** |
| Phase C Security Verification | ✅ unchanged |
| Acceptance Smoke | ✅ unchanged |
| Frontend E2E Core Gate | ✅ unchanged |
| Phase 5 Mocked Regression Gate | Pre-existing cancelled — unchanged |

## End-to-end chain after this commit

| PR | Layer | Status |
|----|-------|--------|
| PR-95..98 | Scheduled delivery core chain | ✅ |
| PR-99..100 | UI hardening + mocked e2e | ✅ |
| PR-101..104 | Full-stack e2e + ledger API + ledger UI | ✅ |
| PR-105 | Ledger operator polish | ✅ |
| PR-106 | Delivery folder tree picker | ✅ |
| PR-107 | Telemetry endpoint (backend) | ✅ |
| **PR-108** | **Telemetry card (frontend)** | **✅ shipped** |

## Non-goals

- No polling / auto-refresh — load once on mount
- No drill-through from chips to filtered ledger views (could be a
  future polish slice)
- No time-range selector — telemetry uses a fixed 24h window by
  design at the backend
- No export of the snapshot
