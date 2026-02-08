# Step: E2E Auth Navigation Helper Expansion (Admin/Permission Specs) Design

## Objective
- Extend shared auth-aware route entry to additional admin/permission E2E specs.
- Reduce intermittent redirect-to-login failures and remove duplicated login wrapper code.

## Scope
- `ecm-frontend/e2e/browse-acl.spec.ts`
- `ecm-frontend/e2e/permissions-dialog.spec.ts`
- `ecm-frontend/e2e/permission-templates.spec.ts`
- `ecm-frontend/e2e/mfa-settings.spec.ts`

## Design

## 1) Replace duplicated login + goto pattern
- Old pattern in each spec:
  - `loginWithCredentialsE2E(...)`
  - `page.goto(...)`
- New pattern:
  - `gotoWithAuthE2E(page, '<target route>', username, password, { token })`

## 2) Cleanup after refactor
- Remove local `loginWithCredentials(...)` wrappers from each migrated spec.
- Remove now-unused `Page` imports and `baseUiUrl` constants.

## 3) Preserve behavior
- Test data setup and assertions remain unchanged.
- Route targets stay the same (`/browse/:id`, `/admin/permission-templates`, `/settings`).
- This step only standardizes authentication/entry behavior in tests.

## Non-Goals
- No backend API updates.
- No UI runtime feature changes.
