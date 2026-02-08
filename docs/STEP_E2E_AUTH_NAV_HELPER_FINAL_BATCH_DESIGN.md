# Step: E2E Auth Navigation Helper Expansion (Remaining Specs) Design

## Objective
- Complete migration of remaining E2E specs that still used direct `loginWithCredentialsE2E + page.goto`.
- Ensure a single auth-aware route-entry pattern across the whole E2E suite.

## Scope
- `ecm-frontend/e2e/version-share-download.spec.ts`
- `ecm-frontend/e2e/pdf-preview.spec.ts`
- `ecm-frontend/e2e/webhook-admin.spec.ts`
- `ecm-frontend/e2e/rules-manual-backfill-validation.spec.ts`

## Design

## 1) Auth-aware route entry unification
- Replaced old sequences:
  - `loginWithCredentialsE2E(...)`
  - `page.goto(...)`
- With:
  - `gotoWithAuthE2E(page, '<target path>', username, password, { token })`

## 2) Per-file cleanup
- Removed local `loginWithCredentials(...)` wrappers.
- Removed unused `Page` imports and `baseUiUrl` constants where no longer needed.
- Kept scenario-specific logic untouched (PDF worker failure route abort, webhook server capture, version/share assertions).

## 3) Behavioral invariants
- Test route targets stay unchanged:
  - `/browse/:id`
  - `/search-results`
  - `/admin/webhooks`
  - `/rules`
- No API or production runtime behavior changed.

## Non-Goals
- No backend functional changes.
- No frontend feature/UI behavior change outside tests.
