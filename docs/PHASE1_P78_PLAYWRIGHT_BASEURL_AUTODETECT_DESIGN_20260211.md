# Phase 1 (P78) - Playwright BaseURL Auto-detect Design (2026-02-11)

## Goal

Avoid running E2E against stale deployed frontend by default.

When `ECM_UI_URL` is not provided, Playwright should auto-detect a usable local frontend URL and prefer source dev server.

## Problem

Recent verification runs showed a mismatch:

- source code changes were validated on `http://localhost:3000`
- Playwright default pointed to `http://localhost:5500`

This caused false negatives/false positives depending on which frontend instance had latest code.

## Scope

- `ecm-frontend/playwright.config.ts` only.
- No application runtime behavior change.
- No backend/API contract change.

## Design

### BaseURL resolution strategy

1. If `ECM_UI_URL` is explicitly set, use it directly.
2. Otherwise probe candidates in order:
   - `http://localhost:3000`
   - `http://localhost:5500`
3. Use first reachable candidate.
4. If neither responds, fall back to `http://localhost:5500` (legacy default).

### Reachability check

- Use `curl` with a short timeout (`1.5s`) and parse HTTP status code.
- Treat `2xx-4xx` as reachable (service is up and responding).

### Developer visibility

- When auto-detect path is used, print selected baseURL in console:
  - `[playwright] ECM_UI_URL not set, using auto-detected baseURL: ...`

## Risk and mitigation

- Risk: environments without `curl` would fail probing.
- Mitigation: local Athena dev environment already uses `curl` in scripts; fallback still points to `5500`.

## API / interface impact

- No user-facing API changes.
- E2E invocation interface unchanged:
  - `ECM_UI_URL` remains supported and takes precedence.
