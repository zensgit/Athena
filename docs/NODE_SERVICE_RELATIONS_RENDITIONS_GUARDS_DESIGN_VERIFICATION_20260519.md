# nodeService relations/renditions Shape Guards: Design and Verification

Date: 2026-05-19

## Scope

First nodeService sub-slice of the frontend service response-shape guard
backlog. nodeService had **zero** guard idiom; this slice introduces the
service-wide bundle and converts only the 13 relations/renditions JSON
methods (nodeService.ts ~1500-1688). No other nodeService method or
subdomain was touched (gate ruling D1).

Guarded methods: `getNodeRelationsSummary`, `getNodeRelationParents`,
`getNodeRelationSources`, `getNodeRelationTargets`,
`getNodeRelationVersions`, `getNodeRelationCheckout`,
`getNodeRelationCheckoutGraph`, `getNodeRelationRenditions`,
`getNodeRelationRendition`, `getNodeRenditionRelationSummary`,
`getNodeRenditionDefinitions`, `requeueNodeRendition`,
`invalidateNodeRendition`.

Out of scope: backend; endpoint paths, payloads, query params (byte-for-byte
unchanged); blob/download/void methods (none in this subdomain); all other
nodeService methods and subdomains; pages; e2e; migrations; `.env` (neither
staged nor modified).

Done directly on `main` (no worktree/cherry-pick): the parent records/mail
integration gate is satisfied and there is no parallel work, mirroring the
tagService round.

## Gate Rulings Applied

- **D0** — exact source names used everywhere (e.g. `getNodeRelationsSummary`,
  plural `Relations`; `getNodeRenditionRelationSummary` is the distinct
  singular method).
- **D1** — single `export const NODE_UNEXPECTED_RESPONSE_MESSAGE`; the
  records/bulk-style `assertUnexpectedResponse(): never` plus generic
  `assertResponse` / `assertResponseArray` / `assertPageResponse(value,
  itemGuard)` helpers, intentionally service-wide for later nodeService
  sub-slices. Only relations/renditions methods changed this round.
- **D2** — `getNodeRelationVersions`, `getNodeRelationCheckoutGraph`,
  `getNodeRelationRenditions` now guard the **raw** response before the
  existing client-side mapping; mapping output is byte-for-byte unchanged
  for valid input. This is a **deliberate semantic tightening, not a
  zero-visible-change**: a malformed/HTML/null raw response now throws
  instead of silently degrading to `[]`/partial. Because every consumer
  call site is error-tolerant (bare try/catch, `Promise.all` → outer
  catch, or inline `.catch(()=>null|[])`), no page crashes; but e.g. in
  AdvancedSearchPage the throw makes the whole relations details section
  read "unavailable" rather than rendering a locally-empty list. This is
  the accepted tightening per `feedback_http_success_is_not_semantic_success`.
- **D3** — `getNodeRelationRendition` has no current consumer but is
  retained and guarded for relations/renditions API-surface consistency
  (unlike the isolated tagService dead methods, removing it would create
  unnecessary API churn).
- **D4** — lightweight deep validation: nested objects the UI reads are
  field-typed, not bare `isObject` (`NodeRenditionMutationResponse.resource`
  and `.queueStatus`, `previewSummary`, `NodeRelationEdge.source/target`,
  the checkout-graph raw incl. nested `ApiVersionResponse`). For
  `NodeCheckoutGraph` raw `nodes`/`edges`: `undefined|null` is accepted
  (mapped to `[]` downstream, existing semantics preserved) but when
  present every element is deep-checked.

## Implementation Notes

- Each method converted from `api.<verb><T>` to `api.<verb><unknown>` plus
  the matching `assert*`; endpoints, query params, and POST bodies (`null`
  body, `{force}`, `{reason,requeue,forceQueue}` defaults) are byte-for-byte
  unchanged.
- `assertPageResponse<T>(value, itemGuard)` validates `content` is an array
  whose every element passes `itemGuard`, plus the four numeric paging
  fields — so a `PageResponse` missing `content` now throws (the prior
  `response.content || []` silent path is the D2 tightening).
- The raw `/relations/checkout-graph` inline wire shape is captured as
  `NodeCheckoutGraphRaw`; the existing `mapVersion` + graph mapping is
  unchanged and runs after the guard.

## Verification

Targeted Jest (new suite plus the gate-required regression set):

```bash
cd ecm-frontend
CI=true npm test -- --runTestsByPath \
  src/services/nodeService.relationsRenditions.test.ts \
  src/services/nodeService.createFolder.test.ts \
  src/services/nodeService.recordProjection.test.ts \
  src/components/preview/DocumentPreview.undeclare.test.tsx --watchAll=false
```

Result:

```text
Test Suites: 4 passed, 4 total
Tests:       10 passed, 10 total
```

The new suite covers: valid-DTO passthrough with endpoint/param/payload
assertions (`maxDepth`, `page/size/relationType`, `majorOnly`, `force`,
`reason/requeue/forceQueue`); byte-for-byte mapping preservation
(`ApiVersionResponse`→`Version`, checkout-graph version mapping, renditions
`content`); HTML/null/missing-field/bad-nested-item → throw
`NODE_UNEXPECTED_RESPONSE_MESSAGE` (including the D2 raw-pre-map throws);
and acceptance of omitted optionals / present optional nested objects. The
createFolder, recordProjection, and DocumentPreview.undeclare suites prove
no regression to the rest of nodeService or its service-mocked consumer
test.

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

Result: PASS. The generic guard helpers, type predicates, the
`NodeCheckoutGraphRaw` type, and the `value as unknown as PageResponse<T>`
cast compile cleanly with no unused-symbol error. (Standalone `npx tsc`
is not the project's typecheck path — it fails inside
`node_modules/react-hook-form` regardless of this change.)

Diff hygiene:

```bash
git diff --check -- . ':!.env'
```

Result: PASS. nodeService.ts: +347 / -34.

## CI Follow-Up

Done directly on `main` (no cherry-pick; the records/mail integration
gate is satisfied). The slice landed in four commits: `4c50333` (guard
code), `5d82d1d` (this doc), `167c731` (the targeted suite — a forward
fix; the suite was omitted from the code commit's `git add`), `6581f88`
(four no-param endpoint `toHaveBeenLastCalledWith` locks for symmetric
endpoint coverage).

Final pushed CI run:

- Run: `26098717250`
- Head: `6581f88`
- Result: PASS

Passing jobs:

- `Backend Verify`
- `Frontend Build & Test`
- `Phase C Security Verification`
- `Phase 5 Mocked Regression Gate`
- `Frontend E2E Core Gate`
- `Property Encryption Closeout Gate`
- `Acceptance Smoke (3 admin pages)`

Bisect accounting — every pushed HEAD of this slice is independently
green:

- `5d82d1d` (guard code + doc, before the test existed) → run
  `26098480476` PASS (proves the code commit is independently sound).
- `167c731` (+ targeted suite) → run `26098512370` PASS.
- `6581f88` (+ endpoint locks) → run `26098717250` PASS (recorded above).

The CI-sensitive checks matched local expectations:

- `Frontend Build & Test` covered lint, type check, build, and the
  relations/renditions suite plus the createFolder / recordProjection /
  DocumentPreview.undeclare regression set.
- `Phase 5 Mocked Regression Gate`, `Frontend E2E Core Gate`, and
  `Acceptance Smoke (3 admin pages)` stayed green — the D2 raw-pre-map
  tightening did not regress any consumer surface (all consumers are
  error-tolerant).
- Backend/security/property-encryption gates stayed green because this
  round did not change backend code or migrations.

## Follow-Up

- Remaining nodeService sub-slices (one at a time, no big-bang): batch-download
  async, search/preview async, folder/node CRUD, lock/checkout, version/history,
  permissions. They reuse the `NODE_UNEXPECTED_RESPONSE_MESSAGE` sentinel and
  the generic helpers introduced here.
