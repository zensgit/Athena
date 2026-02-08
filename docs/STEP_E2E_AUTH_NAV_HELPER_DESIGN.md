# Step: E2E Auth Navigation Helper (Design)

## Objective
- Reduce flaky E2E failures caused by intermittent redirects to login/Keycloak pages during direct route navigation.
- Reuse one auth-aware navigation helper across E2E specs instead of duplicating bypass/login fallback logic.

## Changes

## 1) Shared helper extension
- File: `ecm-frontend/e2e/helpers/login.ts`
- Added:
  - `seedBypassSessionE2E(...)` exported for reuse.
  - `gotoWithAuthE2E(page, targetPath, username, password, { token })`.
- Behavior:
  1. Try token-based bypass session setup.
  2. Navigate to target route.
  3. If URL indicates auth redirect (`/login`, `openid-connect/auth`, `login_required`), perform credential login fallback.
  4. Navigate to target route again.

## 2) Spec refactor: Search preview status
- File: `ecm-frontend/e2e/search-preview-status.spec.ts`
- Removed local duplicated helpers:
  - local `seedBypassSession`
  - local `gotoWithBypassOrLogin`
- Replaced with shared `gotoWithAuthE2E(...)`.

## 3) Spec refactor: Mail automation
- File: `ecm-frontend/e2e/mail-automation.spec.ts`
- Replaced repeated:
  - `loginWithCredentialsE2E(...) + page.goto(...)`
- With:
  - `gotoWithAuthE2E(...)`
- This standardizes route entry resilience for `/admin/mail` and `/admin/mail#diagnostics`.

## Non-Goals
- No runtime application auth behavior change.
- No backend API contract change.
