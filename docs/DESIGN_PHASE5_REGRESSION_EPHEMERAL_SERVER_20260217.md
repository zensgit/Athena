# Design: Phase5 Regression Ephemeral Static Server (2026-02-17)

## Background
- Default `phase5-regression` previously reused `ECM_UI_URL` if reachable.
- In local machines, `http://localhost:5500` may serve stale/static bundles from unrelated processes.
- This caused false negatives in mocked E2E (for example missing new login/session UI behavior).

## Goal
- Make mocked gate deterministic in default mode.
- Preserve opt-in ability to reuse an existing UI target when needed.

## Design
- `scripts/phase5-regression.sh`
  - add env flag: `PHASE5_USE_EXISTING_UI` (default `0`)
  - when target host is local and `PHASE5_USE_EXISTING_UI=0`:
    - build frontend
    - start dedicated static server via `npx serve -s build -l 0`
    - parse assigned localhost port from server log
    - set `EFFECTIVE_ECM_UI_URL` to discovered URL for target check + Playwright run
  - if user sets `PHASE5_USE_EXISTING_UI=1`, script uses existing target behavior.

## Risk
- Low risk; only affects mocked-gate launch path.
- Mitigation:
  - keep explicit override path
  - keep existing fallback for unreachable local URL when not using dedicated mode
