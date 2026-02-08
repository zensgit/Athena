# Step: E2E Login Fallback Stabilization (Design)

## Objective
- Reduce local Playwright flakiness when tests occasionally land on login page despite bypass session seeding.
- Keep existing bypass-first approach and only use UI login as fallback.

## Problem
- In local runs, some tests navigate directly to target pages after injecting token/user into local storage.
- When app auth bootstrap or environment timing causes redirect to login/Keycloak route, tests may fail before functional assertions.

## Design
- Add helper in `ecm-frontend/e2e/search-preview-status.spec.ts`:
  - `gotoWithBypassOrLogin(page, targetPath, username, password, token)`
- Flow:
  1. Seed bypass session (`token`, `user`, `ecm_e2e_bypass`).
  2. Navigate to target route.
  3. If URL indicates auth redirect (`/login`, `openid-connect/auth`, `login_required`):
     - execute `loginWithCredentialsE2E(...)` fallback.
     - navigate to target route again.

## Non-Goals
- No change to runtime auth logic for normal users.
- No global rewrite of all E2E suites in this step; only the affected preview-status suite.

## Safety
- Fallback path is only exercised in E2E context.
- Existing token-based fast path remains first choice.
