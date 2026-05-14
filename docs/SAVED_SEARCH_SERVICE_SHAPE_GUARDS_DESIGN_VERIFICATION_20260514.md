# Saved Search Service Shape Guards Design and Verification

## Context

The recent Transfer Replication and CMIS service slices closed two frontend
service boundaries that could accept SPA HTML fallback as successful API data.
`savedSearchService` had the same risk on a higher-traffic surface:

- Saved Searches page uses list, save, update, pin, delete, and smart-folder
  creation.
- SearchResults and AdminDashboard use pinned saved searches.
- Advanced Search uses built-in saved search templates.
- `nodeSlice.executeSavedSearch` consumes the faceted execute envelope.

Most page tests mock `savedSearchService`, so a mocked or deployed
`index.html` fallback from `/search/saved` could bypass tests and fail only at
runtime. This slice makes the service reject malformed response bodies before
UI code renders or maps them.

## Design

- Add a shared `SAVED_SEARCH_UNEXPECTED_RESPONSE_MESSAGE` for malformed saved
  search responses.
- Guard saved-search readbacks from `save`, `list`, `get`, `update`, and
  `setPinned`.
- Guard built-in templates from `listTemplates`, including nullable
  descriptions and string-array tags.
- Guard `execute(...)` with a faceted envelope validator:
  - optional `results.content` must be an array of result-row objects;
  - each result row must include core `id`, `name`, and `path` fields;
  - optional RM projection, preview, lock/checkout, highlight, tag, category,
    score, and file metadata fields must keep their expected primitive shape;
  - optional `facets` must be a map of `{ value, count }` arrays;
  - `totalHits` and `queryTime` must be numeric when present.
- Guard `createSmartFolder(...)` with the folder readback fields consumed by
  the UI.
- Keep `delete(...)` unchanged because it intentionally consumes no response
  body.
- Keep validators structural rather than enum-exhaustive so backend-added
  template tags, facet names, node types, RM category values, and preview
  statuses remain additive.

## Files Changed

- `ecm-frontend/src/services/savedSearchService.ts`
- `ecm-frontend/src/services/savedSearchService.test.ts`

## Verification

### Targeted Service Test

```bash
cd ecm-frontend
CI=true npm test -- --runTestsByPath src/services/savedSearchService.test.ts --watchAll=false
```

Result:

- 1 suite passed
- 14 tests passed
- New coverage rejects HTML fallback for list and smart-folder responses;
  rejects malformed saved search items, template tags, execute result rows, and
  execute facets; accepts guarded saved-search CRUD readbacks, templates,
  execute envelopes, and smart-folder readbacks.

### Targeted Service and Consumer Tests

```bash
cd ecm-frontend
CI=true npm test -- --runTestsByPath \
  src/services/savedSearchService.test.ts \
  src/pages/SavedSearchesPage.test.tsx \
  src/store/slices/nodeSlice.test.ts \
  --watchAll=false
```

Result:

- 3 suites passed
- 19 tests passed
- Confirms the new service guards remain compatible with the Saved Searches
  page and saved-search execution mapping in `nodeSlice`.

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
git diff --check -- ecm-frontend/src/services/savedSearchService.ts \
  ecm-frontend/src/services/savedSearchService.test.ts \
  docs/SAVED_SEARCH_SERVICE_SHAPE_GUARDS_DESIGN_VERIFICATION_20260514.md
```

Result: passed.

### Remote CI

Pending.

## Residual Work

- This does not add new saved-search product capability.
- Other frontend services may still need similar shape guards; this slice only
  covers the saved-search service boundary.
