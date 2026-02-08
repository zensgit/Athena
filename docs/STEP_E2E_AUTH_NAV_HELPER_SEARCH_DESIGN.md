# Step: E2E Auth Navigation Helper Expansion (Search Specs) Design

## Objective
- Continue consolidating E2E auth + route-entry behavior to reduce login redirect flakes in search flows.
- Remove duplicated local login wrappers from additional search specs.

## Scope
- `ecm-frontend/e2e/search-highlight.spec.ts`
- `ecm-frontend/e2e/search-sort-pagination.spec.ts`
- `ecm-frontend/e2e/search-view.spec.ts`

## Design

## 1) Use shared auth-aware navigation
- Replace per-spec sequence:
  - `loginWithCredentialsE2E(...)`
  - `page.goto(<search route>)`
- With shared helper:
  - `gotoWithAuthE2E(page, '/search-results', username, password, { token })`

## 2) Remove duplicated wrapper code
- Delete local `loginWithCredentials(...)` wrappers from these specs.
- Remove now-unused `Page` import and `baseUiUrl` constant where no longer needed.

## 3) Keep behavior unchanged
- Test assertions, data setup, index wait logic, and route targets remain unchanged.
- This is a test-structure refactor for consistency and stability, not a product behavior change.

## Non-Goals
- No backend API changes.
- No production frontend runtime logic changes.
