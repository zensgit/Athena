# Phase 1 P102 Design: E2E Target Guardrail (3000 vs 5500)

Date: 2026-02-12

## Background

- Two common UI targets exist locally:
  - `http://localhost:3000` (dev server, branch-accurate)
  - `http://localhost:5500` (static/prebuilt bundle, may be stale)
- Running E2E against stale static UI caused false failures during recent phases.

## Goal

Add an explicit preflight guardrail so E2E runs can verify target build mode before execution.

## Scope

- New helper script: `scripts/check-e2e-target.sh`

## Implementation

1. Added target preflight script
- Input:
  - first arg URL, or `ECM_UI_URL`, default `http://localhost:3000`
- Detection:
  - `static/js/bundle.js` -> `dev`
  - `static/js/main.<hash>.js` -> `static`
  - else -> `unknown`
- Behavior:
  - `dev` -> exit `0`
  - `static` -> warning + exit `2` (unless `ALLOW_STATIC=1`)
  - `unknown` -> warning + exit `3`

2. Included guidance for E2E workflow
- Prefer `ECM_UI_URL=http://localhost:3000` for branch validation.

## Expected Outcome

- Developers get immediate, reproducible signal when E2E target is static/stale.
- Reduced false regressions caused by environment mismatch.
