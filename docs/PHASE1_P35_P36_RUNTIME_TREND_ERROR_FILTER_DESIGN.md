# Phase 1 P35/P36 Design: Mail Runtime Trend + Error-to-Diagnostics Linkage

## Scope
- Backend mail runtime metrics:
  - Add runtime trend payload based on current window vs previous window.
  - Keep top error aggregation and expose data for diagnostics drill-down.
- Mail diagnostics query:
  - Add `errorContains` filter for processed mail `errorMessage`.
  - Extend CSV export metadata to include error filter scope.
- Frontend Mail Automation:
  - Add diagnostics filter input `Error contains`.
  - Add URL/localStorage sync for `errorContains`.
  - Make Runtime Health top-error chips clickable to auto-apply diagnostics filters and jump to `#diagnostics`.
  - Show runtime trend chip in Runtime Health.

## Backend Changes
- `MailFetcherService`:
  - `MailRuntimeMetrics` now includes `trend`.
  - New records:
    - `MailRuntimeTrend`
    - `MailRuntimeWindowAggregate` (internal)
  - New trend calculation:
    - Compare current window aggregate with previous window aggregate.
    - Direction rules by error-rate delta:
      - `IMPROVING` if delta <= `-0.05`
      - `WORSENING` if delta >= `0.05`
      - otherwise `STABLE`
  - Diagnostics filter chain now supports `errorContains` via JPA specification (`errorMessage` LIKE).
  - CSV export header includes:
    - `FilterErrorContains`
    - `ErrorFilter`
- `MailAutomationController`:
  - `/integration/mail/diagnostics` accepts `errorContains`.
  - `/integration/mail/diagnostics/export` accepts `errorContains`.

## Frontend Changes
- `mailAutomationService.ts`:
  - `MailDiagnosticsFilters` adds `errorContains`.
  - `MailRuntimeMetrics` adds `trend`.
  - Request params for diagnostics + export include `errorContains`.
- `MailAutomationPage.tsx`:
  - New diagnostics state: `diagnosticsErrorContains`.
  - New query param mapping: `dError`.
  - New filters input: `Error contains`.
  - Active filters chips include `Error`.
  - Export scope summary includes `Error~...`.
  - Runtime Health:
    - Adds trend chip (`Trend IMPROVING/STABLE/WORSENING`).
    - Top-error chips now:
      - set diagnostics status to `ERROR`
      - apply `errorContains`
      - navigate/scroll to `#diagnostics`

## Test Strategy
- Backend:
  - Update controller security/diagnostics tests for new method signatures.
  - Extend diagnostics service test to cover trend payload generation path.
- Frontend:
  - Build validation (`npm run build`).
  - Playwright update:
    - Runtime health test validates top-error chip drill-down to diagnostics filter when data is present.

