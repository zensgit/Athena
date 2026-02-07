# Phase 1 P42 - E2E Login Stabilization Design

## Background

Recent full Playwright runs had intermittent failures in `p1-smoke` and `ui-smoke` caused by Keycloak callback navigation races (`/login` <-> `/protocol/openid-connect/auth`).

The affected tests are not login-feature tests themselves; they are functional smoke validations for search, mail, RBAC, preview, and rule workflows.

## Goal

Make smoke tests deterministic by avoiding fragile UI login redirects in non-login test paths, while still preserving explicit login coverage.

## Scope

- `ecm-frontend/e2e/p1-smoke.spec.ts`
- `ecm-frontend/e2e/ui-smoke.spec.ts`

No backend code changes.
No production runtime behavior changes.

## Design Change

### 1) Prefer token-based session bootstrap in E2E helpers

Updated `loginWithCredentials()` in both files:

- New behavior:
  - If `ECM_E2E_FORCE_UI_LOGIN !== '1'`, helper first tries API token flow:
    - Use provided `token`, or fetch token via `fetchAccessToken(...)`.
    - Inject session via `page.addInitScript()`:
      - `localStorage.token`
      - `localStorage.user`
      - `localStorage.ecm_e2e_bypass = '1'`
    - Navigate directly to `/browse/root`.
- Fallback:
  - If token path cannot be established, keep existing Keycloak UI login flow.

### 2) Keep explicit UI-login coverage intact

- `P1 smoke: login CTA redirects to Keycloak auth endpoint` remains unchanged.
- New helper behavior only affects workflow tests that previously relied on unstable redirect loops.

## Compatibility and Risk

- Risk: low, test-only changes.
- Compatible with existing CI/local runs.
- Supports forcing real UI login when needed by setting:
  - `ECM_E2E_FORCE_UI_LOGIN=1`

## Acceptance Criteria

- `p1-smoke.spec.ts` runs fully green.
- `ui-smoke.spec.ts` runs fully green.
- `search-preview-status.spec.ts` remains green as a regression guard.
