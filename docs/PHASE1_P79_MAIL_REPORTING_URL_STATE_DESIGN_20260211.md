# Phase 1 (P79) - Mail Reporting URL State Persistence Design (2026-02-11)

## Goal

Make Mail Reporting filters refresh-safe/shareable by persisting filter state in URL query params.

## Problem

Mail Reporting filters (`Account`, `Rule`, `Days`) were kept only in component state. Reload/navigation lost filter context, reducing reproducibility during debugging and ops handoff.

## Scope

- Frontend only:
  - `ecm-frontend/src/pages/MailAutomationPage.tsx`
  - `ecm-frontend/e2e/mail-automation.spec.ts`
- No backend/API changes.
- No schema/data migration.

## Design

### New query parameters

- `rAccount`: report account id
- `rRule`: report rule id
- `rDays`: report days window

### Initialization order

1. On page mount, parse report query params from initial URL.
2. Apply valid values to state:
   - `rDays` accepted set: `7 | 14 | 30 | 60 | 90`
   - invalid/missing `rDays` fallback to default `30`
3. Set `reportFiltersLoaded=true`.
4. Load report only after `reportFiltersLoaded` is true.

### URL sync behavior

- On report filter changes, update URL with `navigate(..., { replace: true })`.
- Keep URL clean by omitting defaults:
  - omit `rDays` when value is `30`
  - omit `rAccount`/`rRule` when empty
- Preserve existing query params/hash (including diagnostics params).

## Compatibility

- Existing diagnostics URL state remains unchanged.
- No public contract updates.

## Risk and mitigation

- Risk: URL sync loops.
- Mitigation: compare normalized `currentSearch` and `nextSearch`; navigate only on actual diff.
