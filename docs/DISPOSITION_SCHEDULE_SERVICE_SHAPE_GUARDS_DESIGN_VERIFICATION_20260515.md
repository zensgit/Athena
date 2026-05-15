# Disposition Schedule Service Shape Guards Design and Verification

## Context

The frontend service hardening line continues to close API boundaries
where the SPA HTML fallback or a malformed JSON payload can be treated as
a valid DTO. `dispositionScheduleService` is the frontend consumer of the
Records Management disposition-schedule API
(`/disposition-schedules`, `/folders/{folderId}/disposition-schedule`,
`/folders/{folderId}/disposition-schedule/dry-run`,
`/folders/{folderId}/disposition-schedule/execute`,
`/folders/{folderId}/disposition-schedule/executions`,
`/disposition-schedules/run`). Before this slice every method trusted the
response body shape and forwarded it directly to the typed return value,
so an `index.html` fallback (HTTP 200 with a string body) or a backend
regression that dropped or renamed a field would surface as a downstream
type error in `DispositionSchedulesPage` rather than a clear "unexpected
response" failure.

## Backend Contract Evidence

`ecm-core/src/main/java/com/ecm/core/controller/DispositionScheduleController.java`:

- Mounted at `/api/v1` (the frontend `api.ts` base path adds the
  `/api/v1` prefix, so frontend relative paths remain
  `/disposition-schedules`, `/folders/{folderId}/disposition-schedule`,
  `/folders/{folderId}/disposition-schedule/dry-run`,
  `/folders/{folderId}/disposition-schedule/execute`,
  `/folders/{folderId}/disposition-schedule/executions`,
  `/disposition-schedules/run`).
- `@PreAuthorize("hasRole('ADMIN')")` is enforced at the controller class
  level — the response-shape guard does not change authorization
  semantics.
- `GET /disposition-schedules` →
  `ResponseEntity<List<DispositionScheduleDto>>`.
- `GET /folders/{folderId}/disposition-schedule` →
  `ResponseEntity<DispositionScheduleDto>` (404 when the schedule is not
  found — surfaces as a non-2xx through `api.ts` and never reaches the
  guard).
- `PUT /folders/{folderId}/disposition-schedule` →
  `ResponseEntity<DispositionScheduleDto>`. Body:
  `DispositionScheduleUpsertRequest`
  (`enabled`, `includeSubfolders`, `cutoffAfterDays`,
  `archiveAfterCutoffDays`, `destroyAfterArchiveDays`,
  `archiveStorageTier`, `maxCandidatesPerAction`).
- `DELETE /folders/{folderId}/disposition-schedule` →
  `ResponseEntity<Void>` (HTTP 204, no body).
- `POST /folders/{folderId}/disposition-schedule/dry-run` →
  `ResponseEntity<DispositionDryRunDto>`. Body: optional
  `DispositionScheduleUpsertRequest`; the frontend forwards `{}` when no
  payload is supplied.
- `POST /folders/{folderId}/disposition-schedule/execute` →
  `ResponseEntity<DispositionExecutionDto>`. Body: ignored; the frontend
  forwards `{}`.
- `GET /folders/{folderId}/disposition-schedule/executions` →
  `ResponseEntity<Page<DispositionActionExecutionDto>>` (Spring
  `Pageable`).
- `POST /disposition-schedules/run` →
  `ResponseEntity<DispositionBatchExecutionDto>`.

`DispositionScheduleService` records
(`ecm-core/src/main/java/com/ecm/core/service/DispositionScheduleService.java`):

```
DispositionScheduleDto(UUID id, UUID folderId, String folderName,
    String folderPath, boolean enabled, boolean includeSubfolders,
    Integer cutoffAfterDays, Integer archiveAfterCutoffDays,
    Integer destroyAfterArchiveDays,
    Node.ArchiveStoreTier archiveStorageTier,
    Integer maxCandidatesPerAction, LocalDateTime lastDryRunAt,
    LocalDateTime lastExecutedAt, String lastError)

DispositionCandidateDto(UUID nodeId, String name, String nodeType,
    String path, String actionType, LocalDateTime eligibleAt,
    String blockedByHoldNames)

DispositionDryRunDto(UUID folderId, String folderName,
    boolean includeSubfolders, Node.ArchiveStoreTier archiveStorageTier,
    Integer maxCandidatesPerAction, int cutoffCount, int archiveCount,
    int destroyCount, List<DispositionCandidateDto> candidates)

DispositionExecutionDto(UUID folderId, String folderName,
    int cutoffCount, int archiveCandidateCount, int archivedNodeCount,
    int destroyCandidateCount, int destroyedNodeCount, int failureCount,
    int blockedCount, List<String> failures, String error)

DispositionBatchExecutionDto(int executedSchedules, int cutoffCount,
    int archivedNodeCount, int destroyedNodeCount, int blockedCount,
    int failureCount, List<DispositionExecutionDto> results)

DispositionActionExecutionDto(UUID id, ActionType actionType,
    ExecutionStatus status, UUID nodeId, String nodeName,
    String nodeType, String nodePath, Integer affectedNodeCount,
    String details, String actor, LocalDateTime executedAt)
```

Closed enum unions (serialized as their `name()` strings):

- `Node.ArchiveStoreTier` —
  `HOT | WARM | COLD | GLACIER`
  (`ecm-core/src/main/java/com/ecm/core/entity/Node.java`).
- `DispositionActionExecution.ActionType` —
  `CUTOFF | ARCHIVE | DESTROY`
  (`ecm-core/src/main/java/com/ecm/core/entity/DispositionActionExecution.java`).
- `DispositionActionExecution.ExecutionStatus` —
  `SUCCESS | BLOCKED | FAILED` (same file).

Nullability on the wire:

- `DispositionScheduleDto`:
  - Non-null: `id`, `folderId`, `folderName`, `folderPath`, `enabled`,
    `includeSubfolders`.
  - Nullable: `cutoffAfterDays`, `archiveAfterCutoffDays`,
    `destroyAfterArchiveDays`, `archiveStorageTier`,
    `maxCandidatesPerAction`, `lastDryRunAt`, `lastExecutedAt`,
    `lastError`. (`archiveStorageTier` is normalized to `COLD` on
    upsert, but can be null on a freshly-loaded record that was created
    by another path.)
- `DispositionCandidateDto`:
  - Non-null: `nodeId`, `name`, `nodeType`, `path`, `actionType`,
    `eligibleAt`.
  - Nullable: `blockedByHoldNames` (serialized hold names, only set when
    holds blocked a destroy).
- `DispositionDryRunDto`: all fields populated by the snapshot —
  `archiveStorageTier` is always one of the four tier values, never
  null. `candidates` is always an array (may be empty).
- `DispositionExecutionDto`: all counts are primitive `int` (never
  null); `failures` is always an array (may be empty); `error` is
  nullable.
- `DispositionBatchExecutionDto`: all counts are primitive `int`;
  `results` is always an array (may be empty).
- `DispositionActionExecutionDto`: all string/UUID fields are non-null
  (record built directly from a JPA entity whose columns are
  `nullable = false`); `details` is the only nullable string;
  `affectedNodeCount` is `Integer` and serialized as a finite number.

For `listExecutions`, Spring serializes a
`Page<DispositionActionExecutionDto>` so the JSON envelope always carries
`content`, `totalElements`, `totalPages`, `number`, and `size`. The guard
only enforces these five fields — other Spring page fields (`first`,
`last`, `sort`, `pageable`, `empty`) are ignored so backend metadata
changes do not break the boundary.

## Design

- Export `DISPOSITION_SCHEDULE_UNEXPECTED_RESPONSE_MESSAGE` — a stable,
  user-safe phrasing aligned with sibling guarded services
  (`BLOG_*`, `BULK_IMPORT_*`, `BULK_METADATA_*`, etc.).
- Preserve the public API surface: same nine methods
  (`listSchedules`, `getSchedule`, `upsertSchedule`, `deleteSchedule`,
  `dryRun`, `execute`, `listExecutions`, `runAll`), same return types,
  same endpoint paths, same upsert/dry-run payload shape, same
  `execute`-with-`{}` body, same `runAll`-with-`{}` body, same
  `(page, size)` paging defaults (`page=0`, `size=10`).
- Strengthen the closed-union types in the public DTO surface so callers
  get compile-time narrowing:
  - `archiveStorageTier` on `DispositionScheduleDto` →
    `DispositionArchiveStorageTier | null`.
  - `archiveStorageTier` on `DispositionDryRunDto` →
    `DispositionArchiveStorageTier` (backend always sets this).
  - `actionType` on `DispositionCandidateDto` and
    `DispositionActionExecutionDto` → `DispositionActionType`.
  - `status` on `DispositionActionExecutionDto` →
    `DispositionExecutionStatus`.
  - `DispositionScheduleUpsertRequest.archiveStorageTier` stays
    `string | null` because the form-input value goes the other way
    (frontend → backend) and the page already normalizes
    `'' → null` before sending. Tightening the input type would force a
    cast inside the page without buying any guard.
- Validate every response with a structural guard at the boundary,
  throwing `Error(DISPOSITION_SCHEDULE_UNEXPECTED_RESPONSE_MESSAGE)` on
  any mismatch.
- Convert each `api.get` / `api.post` / `api.put` call to `<unknown>`
  and assert the shape before returning to the caller. `deleteSchedule`
  stays a no-content `api.delete` call and resolves to `void`.

## Files Changed

- `ecm-frontend/src/services/dispositionScheduleService.ts`
- `ecm-frontend/src/services/dispositionScheduleService.test.ts`
- `docs/DISPOSITION_SCHEDULE_SERVICE_SHAPE_GUARDS_DESIGN_VERIFICATION_20260515.md`

## Guard Rules

### `DispositionScheduleDto`

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `id` | string | yes | UUID serialized as string |
| `folderId` | string | yes | UUID |
| `folderName` | string | yes | |
| `folderPath` | string | yes | |
| `enabled` | boolean | yes | |
| `includeSubfolders` | boolean | yes | |
| `cutoffAfterDays` | finite number \| null | yes | |
| `archiveAfterCutoffDays` | finite number \| null | yes | |
| `destroyAfterArchiveDays` | finite number \| null | yes | |
| `archiveStorageTier` | `HOT \| WARM \| COLD \| GLACIER \| null` | yes | Closed enum union |
| `maxCandidatesPerAction` | finite number \| null | yes | |
| `lastDryRunAt` | string \| null | yes | ISO-8601 or null |
| `lastExecutedAt` | string \| null | yes | ISO-8601 or null |
| `lastError` | string \| null | yes | |

### `DispositionCandidateDto`

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `nodeId` | string | yes | |
| `name` | string | yes | |
| `nodeType` | string | yes | |
| `path` | string | yes | |
| `actionType` | `CUTOFF \| ARCHIVE \| DESTROY` | yes | Closed enum union |
| `eligibleAt` | string | yes | ISO-8601 |
| `blockedByHoldNames` | string \| null | yes | |

### `DispositionDryRunDto`

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `folderId` | string | yes | |
| `folderName` | string | yes | |
| `includeSubfolders` | boolean | yes | |
| `archiveStorageTier` | `HOT \| WARM \| COLD \| GLACIER` | yes | Always set by the snapshot — non-null |
| `maxCandidatesPerAction` | finite number | yes | |
| `cutoffCount` | finite number | yes | |
| `archiveCount` | finite number | yes | |
| `destroyCount` | finite number | yes | |
| `candidates` | `DispositionCandidateDto[]` | yes | Every entry guarded |

### `DispositionExecutionDto`

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `folderId` | string | yes | |
| `folderName` | string | yes | |
| `cutoffCount` | finite number | yes | |
| `archiveCandidateCount` | finite number | yes | |
| `archivedNodeCount` | finite number | yes | |
| `destroyCandidateCount` | finite number | yes | |
| `destroyedNodeCount` | finite number | yes | |
| `failureCount` | finite number | yes | |
| `blockedCount` | finite number | yes | |
| `failures` | `string[]` | yes | Backend always serializes as an array |
| `error` | string \| null | yes | |

### `DispositionBatchExecutionDto`

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `executedSchedules` | finite number | yes | |
| `cutoffCount` | finite number | yes | |
| `archivedNodeCount` | finite number | yes | |
| `destroyedNodeCount` | finite number | yes | |
| `blockedCount` | finite number | yes | |
| `failureCount` | finite number | yes | |
| `results` | `DispositionExecutionDto[]` | yes | Every entry guarded |

### `DispositionActionExecutionDto`

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `id` | string | yes | UUID |
| `actionType` | `CUTOFF \| ARCHIVE \| DESTROY` | yes | Closed enum union |
| `status` | `SUCCESS \| BLOCKED \| FAILED` | yes | Closed enum union |
| `nodeId` | string | yes | |
| `nodeName` | string | yes | |
| `nodeType` | string | yes | |
| `nodePath` | string | yes | |
| `affectedNodeCount` | finite number | yes | |
| `details` | string \| null | yes | |
| `actor` | string | yes | |
| `executedAt` | string | yes | ISO-8601 |

### Page envelope (`DispositionPage<T>`)

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `content` | `T[]` | yes | Every entry guarded |
| `totalElements` | finite number | yes | |
| `totalPages` | finite number | yes | |
| `number` | finite number | yes | |
| `size` | finite number | yes | |

Anything that fails any of the above (HTML fallback string, non-array
`content`/`candidates`/`results`/`failures`, missing required field,
wrong-typed nullable field, enum value outside the closed union,
stringified numeric paging field, array entry with a non-matching shape)
throws `Error(DISPOSITION_SCHEDULE_UNEXPECTED_RESPONSE_MESSAGE)`.

`deleteSchedule` is a no-content endpoint (`HTTP 204`) and has no shape
to guard — it resolves to `void` after the `api.delete` call settles.

## Test Coverage

`ecm-frontend/src/services/dispositionScheduleService.test.ts` mocks
`./api` (`get`, `post`, `put`, `delete`) and asserts only observable
behavior — return values, mocked call arguments (URL, paging params,
upsert/dry-run payloads), and thrown error messages. No DOM or network
access is performed.

- `listSchedules`:
  - Forwards `GET /disposition-schedules` and returns a guarded array
    that mixes a fully-populated schedule and a minimal schedule with
    every nullable field set to `null`.
  - HTML fallback rejected.
  - Entry whose `archiveStorageTier` is outside the closed union
    rejected (invalid enum).
- `getSchedule`:
  - Forwards `GET /folders/{folderId}/disposition-schedule` and returns
    a guarded schedule.
  - Accepts a schedule whose `archiveStorageTier` is `null` (covers the
    nullable enum branch).
  - HTML fallback rejected.
  - Malformed schedule whose `cutoffAfterDays` is a string rejected
    (covers the "malformed schedule" requirement).
- `upsertSchedule`:
  - Forwards `PUT /folders/{folderId}/disposition-schedule` with the
    full upsert payload verbatim and returns the guarded schedule.
  - HTML fallback rejected.
- `deleteSchedule`:
  - Forwards `DELETE /folders/{folderId}/disposition-schedule` and
    resolves to `void` (no-content response).
- `dryRun`:
  - Forwards `POST /folders/{folderId}/disposition-schedule/dry-run`
    with `{}` when no payload is provided (default-empty body branch).
  - Forwards the provided payload verbatim when one is supplied.
  - HTML fallback rejected.
  - Malformed dry-run whose candidate `actionType` is outside the
    closed union rejected (covers "malformed dry-run candidate").
  - Malformed dry-run whose `archiveStorageTier` is `null` rejected
    (dry-run requires a non-null tier).
- `execute`:
  - Forwards `POST /folders/{folderId}/disposition-schedule/execute`
    with `{}` and returns the guarded execution.
  - HTML fallback rejected.
  - Malformed execution whose `failures` array contains a non-string
    entry rejected (covers "malformed execution failures").
- `listExecutions`:
  - Default paging (`page=0`, `size=10`) forwards
    `/folders/{folderId}/disposition-schedule/executions` with
    `{ params: { page, size } }`.
  - Custom paging `(2, 25)` forwarded verbatim.
  - HTML fallback rejected.
  - Page item whose `actor` is not a string rejected (covers
    "malformed page item").
  - Page item whose `status` is outside the closed union rejected
    (covers "invalid enum" on the status union).
  - Page envelope with stringified `totalPages` rejected (covers the
    page-envelope numeric-field guard).
- `runAll`:
  - Forwards `POST /disposition-schedules/run` with `{}` and returns
    the guarded batch.
  - HTML fallback rejected.
  - Batch whose nested execution has a malformed `failures` field
    rejected (covers nested-array guard inside the batch).

## Verification

### Targeted Service Test

Intended command:

```bash
cd ecm-frontend
CI=true npm test -- --runTestsByPath src/services/dispositionScheduleService.test.ts --watchAll=false
```

Result: **PASS** after Codex takeover. The main worktree's
`ecm-frontend/node_modules` cache was temporarily symlinked into the
Claude worktree, the targeted suite ran, and the symlink was removed
before staging.

Observed result:

```text
PASS src/services/dispositionScheduleService.test.ts
Test Suites: 1 passed, 1 total
Tests:       27 passed, 27 total
```

### Full Frontend Gates

Intended commands:

```bash
cd ecm-frontend
npm run lint
CI=true npm run build
```

Result: **PASS** after Codex takeover.

Observed commands:

```bash
cd ecm-frontend
npm run lint
CI=true npm run build
```

Observed result:

```text
eslint src --ext .ts,.tsx
Compiled successfully.
```

`CI=true npm run build` emitted the existing CRA bundle-size advisory and
Node `fs.F_OK` deprecation warning, but compilation succeeded.

### Remote CI

Pending combined integration push at the time this document was written.

## Commit

Worktree commit: pending at document write time
(`fix(disposition): guard service responses`).

## Residual Work

- This slice does not add new disposition-schedule product capability.
- `DispositionSchedulesPage` already treats `archiveStorageTier` as a
  nullable, free-string-shaped form value and falls back to `''` /
  `'—'` for display, so the tightened union does not change the page's
  observable behavior. If a future change wants the form to expose only
  the four valid tiers, the form's `archiveStorageTier` state should be
  retyped — out of scope for this guard slice.
- Other frontend services may still need equivalent response-shape
  guards (the broader hardening line is ongoing).
- Combined main-worktree verification should run after cherry-picking the
  disposition slice next to the tenant-service guard slice.
