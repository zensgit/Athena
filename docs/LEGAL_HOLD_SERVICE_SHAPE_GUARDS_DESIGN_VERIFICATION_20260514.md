# Legal Hold Service Shape Guards Design and Verification

## Context

The frontend service hardening line continues to close API boundaries where
the SPA HTML fallback or malformed JSON can be treated as successful DTO data.
`legalHoldService` backs the legal hold admin surface (`LegalHoldsPage`) and is
the only consumer of the `/legal-holds` endpoints exposed to admins. Before
this slice, every method (`listHolds`, `getHold`, `createHold`, `addItems`,
`removeItem`, `releaseHold`) trusted the response body shape directly.

The backend contract comes from `LegalHoldController` and `LegalHoldService`
DTOs (`ecm-core/src/main/java/com/ecm/core/{controller,service}/LegalHold*.java`):

- `LegalHoldController` is mounted at `/api/v1/legal-holds`.
- `GET /legal-holds` returns `List<LegalHoldSummaryDto>`.
- `GET /legal-holds/{holdId}` returns `LegalHoldDto`.
- `POST /legal-holds`, `POST /legal-holds/{holdId}/items`,
  `DELETE /legal-holds/{holdId}/items/{nodeId}`,
  `POST /legal-holds/{holdId}/release` all return one `LegalHoldDto`.
- `LegalHoldSummaryDto(UUID id, String name, String description,
  LegalHold.HoldStatus status, long itemCount, String createdBy,
  LocalDateTime createdDate, String releasedBy, LocalDateTime releasedAt)` —
  `id` and `name` are non-null in DB; `description`, `createdBy`,
  `createdDate`, `releasedBy`, `releasedAt` are nullable. `itemCount` is a
  non-null `long` (Spring Data count) and `status` is the
  `LegalHold.HoldStatus` enum, which currently has values `ACTIVE` and
  `RELEASED` (confirmed by `requireActiveHold` and `releaseHold` flipping
  status to `RELEASED`).
- `LegalHoldItemDto(UUID nodeId, String nodeName, String nodeType,
  String nodePath, LocalDateTime addedAt, String addedBy)` — `nodeId` is the
  primary identifier and is always populated; the remaining fields are
  populated when present but can be null for transient data.
- `LegalHoldDto(UUID id, String name, String description,
  LegalHold.HoldStatus status, String createdBy, LocalDateTime createdDate,
  String releasedBy, LocalDateTime releasedAt, String releaseComment,
  int itemCount, List<LegalHoldItemDto> items)` — extends the summary fields
  with nullable `releaseComment` and an `items` list that is always present
  (the service builds it from `visibleItems`, defaulting to `List.of()` on
  create).

## Design

- Add exported `LEGAL_HOLD_UNEXPECTED_RESPONSE_MESSAGE` with a stable,
  user-safe phrasing consistent with sibling guarded services.
- Constrain `LegalHoldStatus` at runtime to the closed union `'ACTIVE' |
  'RELEASED'`.
- Guard `LegalHoldSummary` with:
  - required string `id` and `name`;
  - required `status` constrained to the union;
  - required finite numeric `itemCount`;
  - nullable string `description`, `createdBy`, `createdDate`, `releasedBy`,
    `releasedAt`.
- Guard `LegalHoldItem` with:
  - required string `nodeId`;
  - nullable string `nodeName`, `nodeType`, `nodePath`, `addedAt`, `addedBy`.
- Guard `LegalHoldDetail` as a `LegalHoldSummary` plus:
  - nullable string `releaseComment`;
  - required `items` array where every element passes the `LegalHoldItem`
    guard.
- Guard the public methods:
  - `listHolds` rejects anything that is not an array of valid
    `LegalHoldSummary`. HTML fallback strings, page envelopes, and malformed
    summaries throw `Error(LEGAL_HOLD_UNEXPECTED_RESPONSE_MESSAGE)`.
  - `getHold`, `createHold`, `addItems`, `removeItem`, `releaseHold` reject
    any response that is not a valid `LegalHoldDetail`.
- Preserve the public API surface (method names and return types) and the
  existing endpoint paths and request payloads.

## Files Changed

- `ecm-frontend/src/services/legalHoldService.ts`
- `ecm-frontend/src/services/legalHoldService.test.ts`
- `docs/LEGAL_HOLD_SERVICE_SHAPE_GUARDS_DESIGN_VERIFICATION_20260514.md`

## Guard Rules Summary

### LegalHoldSummary

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `id` | string | yes | UUID, serialized as string |
| `name` | string | yes | DB non-null |
| `status` | `'ACTIVE' \| 'RELEASED'` | yes | Closed union check |
| `itemCount` | number | yes | Finite number (backend `long`) |
| `description` | string \| null | no | Nullable |
| `createdBy` | string \| null | no | Audit, may be null |
| `createdDate` | string \| null | no | Audit, may be null |
| `releasedBy` | string \| null | no | Null while ACTIVE |
| `releasedAt` | string \| null | no | Null while ACTIVE |

### LegalHoldItem

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `nodeId` | string | yes | UUID, serialized as string |
| `nodeName` | string \| null | no | Node display name |
| `nodeType` | string \| null | no | Enum name, serialized as string |
| `nodePath` | string \| null | no | Snapshot at hold time |
| `addedAt` | string \| null | no | Timestamp |
| `addedBy` | string \| null | no | Audit user |

### LegalHoldDetail

Extends `LegalHoldSummary` with:

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `releaseComment` | string \| null | no | Set on release |
| `items` | `LegalHoldItem[]` | yes | Always present, may be empty |

Anything else (HTML fallback, bare object missing `items`, malformed summary
or item, unsupported `status`, non-finite `itemCount`, or wrong-typed nullable
field) throws `Error(LEGAL_HOLD_UNEXPECTED_RESPONSE_MESSAGE)`.

## Verification

### Targeted Service Test

Intended command:

```bash
cd ecm-frontend
CI=true npm test -- --runTestsByPath src/services/legalHoldService.test.ts --watchAll=false
```

Result: **PASS**. Re-run by Codex after Claude produced the files. The Claude
worktree had no local `node_modules`, so verification temporarily reused the
main worktree dependency cache through a symlink that was removed before
staging. `legalHoldService.test.ts` ran 19 tests, 0 failures.

Test coverage authored in `legalHoldService.test.ts` (assertions are all
observable — return values, mocked call arguments, and thrown error
messages; no DOM access):

- `listHolds` success with both fully populated and nullable summaries;
  asserts the `/legal-holds` path is forwarded.
- `listHolds` rejects HTML fallback.
- `listHolds` rejects a page envelope (object with a `content` array).
- `listHolds` rejects an item with unsupported `status`.
- `listHolds` rejects an item with non-finite `itemCount` (string).
- `listHolds` rejects an item with a non-string-or-null audit field.
- `getHold` success with a detail that uses nullable summary fields and an
  empty items array; asserts the `/legal-holds/{id}` path is forwarded.
- `getHold` rejects HTML fallback.
- `getHold` rejects a detail missing the `items` array.
- `getHold` rejects a detail whose `items` contains an entry with a non-string
  `nodeId`.
- `getHold` rejects a detail whose `releaseComment` is not string-or-null.
- `createHold`, `addItems`, `removeItem`, `releaseHold` each have a happy-path
  test that forwards payload/path and a malformed readback test that asserts
  `LEGAL_HOLD_UNEXPECTED_RESPONSE_MESSAGE`. The `removeItem` mock uses the
  separate `api.delete` channel; `releaseHold` malformed readback uses an HTML
  fallback to exercise the most realistic SPA index-html failure.

### Full Frontend Gates

Intended commands:

```bash
cd ecm-frontend
npm run lint
CI=true npm run build
```

Result: **PASS**. `npm run lint` completed cleanly. `CI=true npm run build`
initially caught a TypeScript direct-cast issue in `isLegalHoldDetail`; Codex
changed the cast to pass through `unknown` and the production build then
completed cleanly with the existing CRA bundle-size advisory.

### Remote CI

Not triggered; this slice is committed in the worktree only and not pushed,
per the task scope.

## Residual Work

- This slice does not add new legal hold product capability.
- Other frontend services may still need equivalent response-shape guards
  (the broader hardening line is ongoing).
