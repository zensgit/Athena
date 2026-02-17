# Verification: Phase5 Regression Ephemeral Static Server (2026-02-17)

## Scope
- Validate default `scripts/phase5-regression.sh` behavior avoids stale `:5500` bundles by running against dedicated ephemeral static server.
- Confirm complete delivery gate remains green in default mode.

## Changes Under Test
- `scripts/phase5-regression.sh`
  - new default `PHASE5_USE_EXISTING_UI=0`
  - starts `npx serve -s build -l 0` and uses discovered localhost URL for Playwright

## Commands and Results
1. Syntax check
```bash
bash -n scripts/phase5-regression.sh
bash -n scripts/phase5-phase6-delivery-gate.sh
```
- Result: PASS

2. Phase5 regression default run
```bash
./scripts/phase5-regression.sh
```
- Result: PASS (`10 passed`)
- Observed runtime evidence:
  - `phase5_regression: starting dedicated static SPA server (ephemeral port)`
  - `phase5_regression: using dedicated server http://localhost:<random-port>`

3. Full delivery gate default run
```bash
./scripts/phase5-phase6-delivery-gate.sh
```
- Result: PASS
  - mocked gate: `10 passed`
  - full-stack admin smoke: `1 passed`
  - phase6 mail integration smoke: `1 passed`
  - phase5 search suggestions integration smoke: `1 passed`
  - p1 smoke: `3 passed, 1 skipped`

## Conclusion
- Default gate path no longer depends on whatever is bound to local `:5500`.
- Auth session recovery mocked E2E is stable in default execution path.
