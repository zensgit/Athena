# Category Service Shape Guards Design and Verification

## Context

The current frontend hardening line is closing service boundaries that can
mistake SPA HTML fallback or malformed JSON for valid API data. `categoryService`
is a small but shared surface:

- `CategoryManager` loads the category tree and node category assignments.
- `MLSuggestionsDialog` loads the category tree and applies a suggested
  category.
- `BulkMetadataDialog` loads the category tree as bulk metadata options.

Those consumers do not have dedicated tests that exercise the real service
implementation, so malformed `/categories` or `/nodes/{id}/categories`
responses could pass mocked UI tests and fail at runtime. This slice makes the
service reject malformed response bodies before components flatten or render
them.

## Design

- Add a shared `CATEGORY_UNEXPECTED_RESPONSE_MESSAGE` for malformed category
  responses.
- Guard `getCategoryTree()` with a recursive tree-node validator:
  - required `id`, `name`, `path`, and numeric `level`;
  - nullable/optional `description`;
  - required `children` array, recursively validated.
- Guard `createCategory(...)` and `updateCategory(...)` with the category
  readback shape consumed by the UI.
- Guard `getNodeCategories(...)` with an array of category readbacks.
- Keep `deleteCategory(...)`, `addCategoryToNode(...)`, and
  `removeCategoryFromNode(...)` unchanged because these endpoints intentionally
  return no body and current consumers only await success/failure.
- Keep validators structural rather than enum-exhaustive. Category names,
  paths, and hierarchy depth remain backend-owned data.

## Files Changed

- `ecm-frontend/src/services/categoryService.ts`
- `ecm-frontend/src/services/categoryService.test.ts`

## Verification

### Targeted Service Test

```bash
cd ecm-frontend
CI=true npm test -- --runTestsByPath src/services/categoryService.test.ts --watchAll=false
```

Result:

- 1 suite passed
- 9 tests passed
- New coverage rejects HTML fallback for category trees and node category
  lists; rejects malformed nested tree children and mutation readbacks; accepts
  guarded tree, create/update, node-category list, and void mutation endpoint
  wiring.

### Consumer Test Availability

No existing `CategoryManager`, `BulkMetadataDialog`, or `MLSuggestionsDialog`
test file is present in this checkout. Consumer compatibility for this slice is
therefore covered by TypeScript, lint, and production build rather than a
targeted component regression suite.

### Frontend Lint

```bash
cd ecm-frontend
npm run lint
```

Result: passed.

### Production Build

```bash
cd ecm-frontend
CI=true npm run build
```

Result: compiled successfully. CRA still reports the existing bundle-size
advisory. Node emitted the known dependency deprecation warning for `fs.F_OK`;
it did not fail the build.

### Diff Hygiene

```bash
git diff --check -- ecm-frontend/src/services/categoryService.ts \
  ecm-frontend/src/services/categoryService.test.ts \
  docs/CATEGORY_SERVICE_SHAPE_GUARDS_DESIGN_VERIFICATION_20260514.md
```

Result: passed.

### Remote CI

Run: `25837718003`

Commit: `4eafb33 fix(categories): guard service responses`

Result: passed.

- Backend Verify: passed
- Frontend Build & Test: passed
- Phase C Security Verification: passed
- Property Encryption Closeout Gate: passed
- Frontend E2E Core Gate: passed
- Acceptance Smoke (3 admin pages): passed
- Phase 5 Mocked Regression Gate: passed

## Residual Work

- This does not add new category product capability.
- Void category mutation endpoints still trust HTTP success/failure rather than
  response-body shape because they are designed as no-content endpoints.
- Other frontend services may still need similar shape guards; this slice only
  covers category service reads and readbacks.
