# nodeService folder/node CRUD Shape Guards: Design and Verification

Date: 2026-05-20

## Scope

Fifth nodeService sub-slice. Covers folder/node CRUD: **16 public JSON methods
+ 1 internal helper (`getRootFolder`)** = 17 raw-response entry points
guarded. Three distinct Node-flavored backend DTOs are handled
(`FolderResponse`, `ApiNodeResponse`, `ApiNodeDetailsResponse`), plus the
`Node` output type directly for two zero-mapper methods, plus
`NodeRelationEdge` reused via the relations/renditions slice predicate.

The shared bundle introduced by earlier sub-slices is reused
(`NODE_UNEXPECTED_RESPONSE_MESSAGE`, `isObject`, `isFiniteNumber`,
`isStringOrNullish`, `isBooleanOrNullish`, `isStringArray`, `isNullishOr`,
`isOptionalFiniteNumber`, `assertResponse`, `assertResponseArray`,
`assertPageResponse`, `isNodeRelationEdge`). This slice adds:

- `isStringOrNullishOrTimestampArray` — accepts ISO string, null/undefined,
  or Jackson `LocalDateTime` array `[Y, M, D, h, m, s[, nanos]]`.
- `normalizeTimestamp` — coerces array form to ISO string before the
  mapper sees it, preserving string/null/undefined as-is.
- `isFolderResponse` / `isApiNodeResponse` / `isApiNodeDetailsResponse` —
  per-DTO predicates with lenient defaults per
  `feedback_guard_predicates_real_backend_shape_drift`.
- `isApiNodeResponseListEnvelope` / `isApiNodeDetailsResponseListEnvelope`
  — the two distinct `{content, …}` shapes for getChildren / getChildrenPage.
- `isNode` (lenient) — for `addAspect` / `removeAspect` only.
- `assertAndNormalize*` wrappers that fuse guard + timestamp normalization
  so the existing mappers (`folderToNode`, `apiNodeToNode`,
  `apiNodeDetailsToNode`) never see a Jackson array.

Guarded methods (17):

- `getRootFolder` (internal, called by `getNode('root')`, `getChildren('root')`,
  `getChildrenPage('root')`, `createFolder('root')`)
- `getFolderByPath`, `getNode`
- `getChildren`, `getChildrenPage` (each with try/catch dual-endpoint fallback)
- `createFolder`, `updateNode`, `moveNode`, `copyNode`
- `addAspect`, `removeAspect`
- `getTargetAssociations`, `createTargetAssociation`, `getSourceAssociations`,
  `addSecondaryChild`, `getSecondaryChildren`, `getSecondaryParents`

Out of scope: backend; endpoint paths / payloads / query params
(byte-for-byte unchanged); the three void methods `deleteNode`,
`removeTargetAssociation`, `removeSecondaryChild`; multipart `uploadDocument`
and blob `downloadDocument`; lock/checkout, version/history, permissions —
all subsequent sub-slices; pages; e2e; migrations; `.env`. The mapper
bodies (`folderToNode` / `apiNodeToNode` / `apiNodeDetailsToNode`) are
unchanged.

Done directly on `main`.

## Gate Rulings Applied

- **H1 — lenient required-string scope**: only `id`, `name`, `path` are
  strict required strings (mapper-required for a usable Node). `nodeType`
  validated as `typeof === 'string'` (union not enforced). `createdBy` /
  `lastModifiedBy` / other usernames → `isStringOrNullish` (real backend
  may emit null in some code paths; `apiNodeDetailsToNode` already does
  `createdBy || ''` defensively). LocalDateTime fields (`createdDate`,
  `lastModifiedDate`, `checkoutDate`) → `isStringOrNullishOrTimestampArray`
  (Jackson `LocalDateTime` can serialize as `[Y, M, D, h, m, s[, nanos]]`
  array). `createdBy` is **not** in the timestamp-array set — it is
  String on the wire.
- **H2 — three independent Node-flavored predicates**: `isFolderResponse`,
  `isApiNodeResponse`, `isApiNodeDetailsResponse` each capture the field
  set distinct to that DTO (folder-only fields like `folderType` /
  `smart` / `queryCriteria` vs document-shaped fields like `size` /
  `contentType` / `locked` / `checkedOut`).
- **H3(a) — dual-endpoint silent-fallback semantic preserved**: the
  catch-all on lines wrapping `/folders/{id}/contents` swallows guard
  throws and re-routes to `/nodes/{id}/children`. The lenient primary
  guard only triggers fallback on structural breaks (envelope `content`
  not array, items missing `id`/`name`/`path`), not on legitimate field
  variance. This matches the original intent of the dual-endpoint design
  (infrastructure-shape differences). Verified by tests.
- **H4 — lenient `isNode` for `addAspect` / `removeAspect`**: these two
  methods historically return the backend `NodeDto` shape directly with
  no client-side mapper. They currently have **zero downstream
  consumers** of the returned Node (PropertiesDialog discards the
  result and reloads dialog data via a separate call). This slice only
  adds a defensive guard that requires `id/name/nodeType/path` strings;
  the mapper-less path is **not** refactored.
- **H5 — `isNodeRelationEdge` reused as-is** for the six
  association/secondary methods. No new predicate added for these.
- **H6 — semantic-tightening framing (corrected per gate)**: post-guard,
  malformed raw throws `NODE_UNEXPECTED_RESPONSE_MESSAGE` from these
  methods. Consumer surfaces split into two classes:
  - **Redux thunk path** (`nodeSlice.ts:17+` — `createFolder`,
    `updateNode`, `moveNode`, `copyNode`, `getChildrenPage` indirectly):
    throw becomes the thunk's rejected state; the slice's rejected
    reducer or the UI selector surfaces the failure (error toast or
    state preserved). **Not** a local try/catch.
  - **Direct page/dialog path** (`FolderTree`, `FileBrowser`,
    `PropertiesDialog`, `AssociationManager`, `SearchResults`, etc.):
    local try/catch + `toast.error`.
  Both classes safely absorb the new throw; no page crashes.
- The `getChildren` / `getChildrenPage` dual-endpoint fallback is a third
  class: throws inside the primary's `try` block are silently caught and
  routed to the fallback endpoint (intended behavior preserved).

## Implementation Notes

- The normalization sandwich (`assertAndNormalize*`) is critical:
  predicate accepts `string | null | number[]` for LocalDateTime fields,
  then `normalizeTimestamp` coerces `number[]` → ISO 8601 string before
  the mapper assigns it to `Node.created` (declared `string`). Without
  this layer, accepting the array form in the predicate would leak a
  non-string value into `Node.created` and downstream UI.
- `pickPrimaryRoot` (line 1736) calls
  `a.createdDate.localeCompare(b.createdDate)` — assumes string. The
  `getRootFolder` normalization step (running before
  `pickPrimaryRoot`) guarantees `createdDate` is string by that point.
- The mappers themselves (`folderToNode` etc.) are entirely unchanged.

## Verification

Targeted Jest (full gate-required regression set):

```bash
cd ecm-frontend
CI=true npm test -- --runTestsByPath \
  src/services/nodeService.folderNodeCrud.test.ts \
  src/services/nodeService.searchProper.test.ts \
  src/services/nodeService.previewSide.test.ts \
  src/services/nodeService.batchDownloadAsync.test.ts \
  src/services/nodeService.relationsRenditions.test.ts \
  src/services/nodeService.createFolder.test.ts \
  src/services/nodeService.recordProjection.test.ts --watchAll=false
```

Result:

```text
Test Suites: 7 passed, 7 total
Tests:       52 passed, 52 total
```

The two pre-existing nodeService test files (`createFolder.test.ts`,
`recordProjection.test.ts`) survive the new lenient guards without
modification — both use ISO-string `createdDate` in their fixtures and
pass through the lenient predicates and the normalization no-op path
(string in, string out).

The new suite covers per gate matrix:

- `getRootFolder` valid + Jackson-array normalization
- `getRootFolder` rejection: HTML / null / bad array element (missing id)
- `getFolderByPath` valid + endpoint/params lock
- `getNode` regular id + `'root'` branch (verifies `getRootFolder` →
  `folderToNode` chain)
- `createFolder` normal POST body lock + `'root'` parentId resolution via
  `getRootFolder`
- `updateNode` PATCH endpoint lock
- `moveNode` / `copyNode` POST endpoint + body lock
- `getChildren` primary good (no fallback fired)
- `getChildren` primary bad → silent fallback good (H3(a) verification)
- `getChildren` primary bad → fallback also bad → throw
- `getChildren('root')` resolves via `getRootFolder` then queries by
  resolved id
- `getChildrenPage` dual-endpoint + total preservation + root resolution
- `addAspect` / `removeAspect` lenient `isNode` (minimal id/name/nodeType/path
  passes; missing required → throw)
- Six association methods reuse `isNodeRelationEdge` with valid + malformed
- Jackson timestamp array on `createdDate` → mapper output is ISO string
- `createdBy` null + `lastModifiedDate` null accepted (lenient); mapper
  output preserves null where original code would
- Missing `id` / `name` / `path` across all three DTOs → throw

Frontend lint:

```bash
cd ecm-frontend
npm run lint
```

Result: PASS.

Frontend production build (authoritative typecheck — `react-scripts`,
what CI runs):

```bash
cd ecm-frontend
CI=true npm run build
```

Result: PASS.

Diff hygiene:

```bash
git diff --check -- . ':!.env'
```

Result: PASS. nodeService.ts: +251 / -25.

## E2E Risk Notes

`folder/node CRUD` directly powers the FolderTree / FileBrowser / search
result preview chain — the same path that exposed the search-proper E2E
regression. The lenient predicate strategy (`id`/`name`/`path` strict +
everything else nullish, plus Jackson-array timestamp tolerance with
normalization) was designed against that lesson. If the Frontend E2E
Core Gate flags a regression after push, the diagnostic priority order
per `feedback_guard_predicates_real_backend_shape_drift` is:

1. Capture the failing spec's network response shape (trace.zip);
2. Identify the specific field rejected by the predicate;
3. Widen the predicate (lean lenient), not the test mocks;
4. Add a `runtimeSparse*Item`-style fixture matching the real wire.

## Follow-Up

- Remaining nodeService sub-slices: lock/checkout (the multi-method
  group around `getLockInfo` / `lockNodeTyped` / `unlockNode` /
  `getCheckoutInfo` / `getCheckoutLineage` / `checkoutDocument` /
  `cancelCheckoutDocument` / `checkinDocument`), version/history, and
  permissions. All reuse the bundle plus the new `normalizeTimestamp` /
  `isStringOrNullishOrTimestampArray` / `assertAndNormalize*` helpers.
