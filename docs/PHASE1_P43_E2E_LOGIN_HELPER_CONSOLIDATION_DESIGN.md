# Phase 1 P43 - E2E Login Helper Consolidation Design

## Background

E2E specs contained many duplicated `loginWithCredentials` implementations.
This increased maintenance cost and reintroduced Keycloak callback flakiness across files.

## Goal

1. Consolidate token-first login strategy into a shared helper for remaining specs.
2. Keep existing spec-level call sites stable with minimal behavior change.

## Scope

### New shared helper

- `ecm-frontend/e2e/helpers/login.ts`

### Specs migrated to shared helper

- `ecm-frontend/e2e/mail-automation.spec.ts`
- `ecm-frontend/e2e/search-highlight.spec.ts`
- `ecm-frontend/e2e/search-sort-pagination.spec.ts`
- `ecm-frontend/e2e/search-view.spec.ts`
- `ecm-frontend/e2e/version-share-download.spec.ts`
- `ecm-frontend/e2e/mfa-settings.spec.ts`
- `ecm-frontend/e2e/search-preview-status.spec.ts`
- `ecm-frontend/e2e/webhook-admin.spec.ts`
- `ecm-frontend/e2e/permissions-dialog.spec.ts`
- `ecm-frontend/e2e/browse-acl.spec.ts`
- `ecm-frontend/e2e/permission-templates.spec.ts`
- `ecm-frontend/e2e/pdf-preview.spec.ts`
- `ecm-frontend/e2e/rules-manual-backfill-validation.spec.ts`

## Design Details

### 1) Shared login helper behavior

`loginWithCredentialsE2E(page, username, password, { token })` now provides:

- token-first session bootstrap:
  - use provided token; otherwise fetch via `fetchAccessToken`
  - set `localStorage.token`, `localStorage.user`, `localStorage.ecm_e2e_bypass`
  - navigate to `/browse/root` and assert app shell visible
- role inference by username:
  - admin -> `ROLE_ADMIN`
  - editor -> `ROLE_EDITOR`
  - viewer -> `ROLE_VIEWER`
- UI-login fallback path retained when token path unavailable
- `ECM_E2E_FORCE_UI_LOGIN=1` support retained

### 2) Spec migration approach

- Each migrated spec keeps a local `loginWithCredentials(...)` wrapper to avoid large call-site edits.
- Wrapper delegates to `loginWithCredentialsE2E(...)`.
- This keeps diff small while removing duplicate auth logic.

### 3) Stability fixes during consolidation

- `ecm-frontend/e2e/p1-smoke.spec.ts`
  - Keycloak CTA click changed to `noWaitAfter` + short URL wait to avoid click/navigation race.
- `ecm-frontend/e2e/permissions-dialog.spec.ts`
  - row lookup made resilient to list ordering/pagination by:
    - trying created folder row first
    - fallback to first visible `e2e-permissions-*` row
    - using generic row action button selector.

## Risk

- Low risk: test-only changes.
- No production runtime behavior changes.

## Acceptance Criteria

- Migrated specs still pass under full Playwright run.
- No regressions in `p1-smoke` and `ui-smoke`.
- End-to-end run is green except existing intentional skips.
