# Phase 116: Search Recoverable Event Coverage

## Date
2026-02-25

## Background
- Search temporary failure recovery flow (`503` -> `Retry` -> success) was covered by mocked E2E.
- Recovery guard expected set did not explicitly include search recoverable flow markers.

## Goals
1. Emit structured recovery marker when recoverable search error alert is shown.
2. Emit structured recovery marker when retry path succeeds.
3. Add both search recovery markers to `phase5-regression` expected event set.

## Changes

### 1) Search recoverable markers
- `ecm-frontend/e2e/search-suggestions-save-search.mock.spec.ts`
  - in temporary backend failure scenario, emits:
    - `recovery_event:search_recoverable_error_alert_shown`
    - `recovery_event:search_recoverable_retry_success`

### 2) Recovery guard expected set expansion
- `scripts/phase5-regression.sh`
  - expected recovery events list now includes:
    - `search_recoverable_error_alert_shown`
    - `search_recoverable_retry_success`

## Impact
- No product runtime behavior change.
- Recovery telemetry guard now covers search recoverable-error feedback and retry success closure.
