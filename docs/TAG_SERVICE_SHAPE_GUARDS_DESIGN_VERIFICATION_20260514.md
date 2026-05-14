# Tag Service Shape Guards Design and Verification

## Context

The current frontend hardening line is closing service boundaries that can
mistake SPA HTML fallback or malformed JSON for valid API data. `tagService` is
a shared surface across tag administration, mail automation, bulk metadata, and
ML suggestion flows:

- `TagManager` loads, searches, creates, updates, deletes, merges, and assigns
  tags.
- `MailAutomationPage` loads tags alongside mail folders and provider data.
- `BulkMetadataDialog` loads tags as bulk metadata options.
- `MLSuggestionsDialog` applies suggested tags to nodes.

Most of those flows mock service responses in UI tests, so malformed `/tags` or
`/nodes/{id}/tags` responses could pass mocked CI while failing at runtime. This
slice makes the service reject malformed response bodies before components
render, iterate, or use readback data.

## Design

- Add a shared `TAG_UNEXPECTED_RESPONSE_MESSAGE` for malformed tag responses.
- Guard tag list endpoints with a structural `Tag` validator:
  - required `id`, `name`, `color`, `created`, and `creator` strings;
  - required finite numeric `usageCount`;
  - nullable/optional `description`.
- Guard `createTag(...)` and `updateTag(...)` readbacks with the same `Tag`
  shape.
- Guard `getTagCloud()` with a structural `TagCloudItem` validator:
  - required `name` and `color` strings;
  - required finite numeric `count`.
- Guard `getNodeTags(...)` with the `Tag[]` validator.
- Keep `deleteTag(...)`, `mergeTags(...)`, `addTagToNode(...)`,
  `addTagsToNode(...)`, and `removeTagFromNode(...)` unchanged because current
  consumers only await HTTP success/failure and the endpoints intentionally do
  not provide a useful response body.
- Leave `findNodesByTag(...)` and `findNodesByTags(...)` unchanged in this
  slice. They are not used by current frontend consumers in this checkout, and
  no matching backend controller route was present in the quick controller
  search. Guarding their unknown payload should be done with the future consumer
  contract rather than a guessed shape.

## Files Changed

- `ecm-frontend/src/components/tags/TagManager.tsx`
- `ecm-frontend/src/services/tagService.ts`
- `ecm-frontend/src/services/tagService.test.ts`

## Verification

### Targeted Service Test

```bash
cd ecm-frontend
CI=true npm test -- --runTestsByPath src/services/tagService.test.ts --watchAll=false
```

Result:

- 1 suite passed
- 12 tests passed
- New coverage rejects HTML fallback for tag lists; rejects malformed tag list
  items, mutation readbacks, and tag cloud items; accepts guarded tag lists,
  create/update readbacks, tag cloud responses, node tag reads, and void
  mutation endpoint wiring.

### Consumer Test Availability

No existing `TagManager` component test file is present in this checkout. The
service is also consumed by `MailAutomationPage`, `BulkMetadataDialog`, and
`MLSuggestionsDialog`. Consumer compatibility for this slice is therefore
covered by TypeScript, lint, and production build rather than a dedicated
component regression suite.

The production build exposed one real contract drift: `TagManager` kept a local
`Tag` interface with `description?: string`, while the backend/service contract
allows `description: null`. The component now reuses the exported service `Tag`
type instead of duplicating a narrower local contract.

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
git diff --check -- ecm-frontend/src/services/tagService.ts \
  ecm-frontend/src/services/tagService.test.ts \
  ecm-frontend/src/components/tags/TagManager.tsx \
  docs/TAG_SERVICE_SHAPE_GUARDS_DESIGN_VERIFICATION_20260514.md
```

Result: passed.

### Remote CI

Run: `25839328347`

Commit: `ead9b3b fix(tags): guard service responses`

Result: passed.

- Backend Verify: passed
- Frontend Build & Test: passed
- Phase C Security Verification: passed
- Property Encryption Closeout Gate: passed
- Frontend E2E Core Gate: passed
- Acceptance Smoke (3 admin pages): passed
- Phase 5 Mocked Regression Gate: passed

## Residual Work

- This does not add new tag product capability.
- Void tag mutation endpoints still trust HTTP success/failure rather than
  response-body shape because they are designed as no-content endpoints.
- Unused `findNodesByTag(...)` and `findNodesByTags(...)` remain unguarded until
  a concrete frontend consumer or backend response contract defines the expected
  payload.
- Other frontend services may still need similar shape guards; this slice only
  covers tag service reads and readbacks used by current consumers.
