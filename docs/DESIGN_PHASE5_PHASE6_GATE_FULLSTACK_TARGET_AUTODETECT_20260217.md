# Design: Phase5/Phase6 Gate Fullstack Target Auto-Detect (2026-02-17)

## Background
- `scripts/phase5-phase6-delivery-gate.sh` previously defaulted `ECM_UI_URL_FULLSTACK` to `http://localhost`.
- In local environments, `http://localhost` can point to static/prebuilt assets, while current branch code runs on `http://localhost:3000`.
- This could cause gate execution against stale UI bundles.

## Goal
- Keep current explicit configuration behavior unchanged.
- When `ECM_UI_URL_FULLSTACK` is not explicitly provided, automatically pick a more accurate local target.

## Design
- Add reachability probe helper:
  - `is_http_reachable(url)` using `curl -fsS --max-time 2`.
- Add resolver:
  - `resolve_fullstack_ui_url()`
  - if `ECM_UI_URL_FULLSTACK` is set: use it directly.
  - else probe in order:
    1. `http://localhost:3000`
    2. `http://localhost`
  - fallback: `http://localhost`.
- Emit log when auto-detected target is used.
- Add configurable static-target policy for full-stack smoke:
  - Gate env: `ECM_FULLSTACK_ALLOW_STATIC` (default `1`)
  - Propagated to child scripts as `FULLSTACK_ALLOW_STATIC`
  - Child scripts pass it to `check-e2e-target.sh` via `ALLOW_STATIC`
  - Gate `p1 smoke` stage runs the same target check before executing Playwright
  - `0` means strict mode (static target fails fast), `1` keeps compatibility.

## Non-Goals
- No change to mocked gate target semantics.
- No change to any Playwright spec logic.
- No forced override of user-provided `ECM_UI_URL_FULLSTACK`.

## Risk
- Very low: only affects default-path URL selection in gate script.
- Mitigated by preserving environment override precedence.
