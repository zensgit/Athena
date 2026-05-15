# Bulk Import Service Shape Guards Design and Verification

## Context

The frontend service hardening line continues to close API boundaries where
the SPA HTML fallback or a malformed JSON payload can be treated as a valid
DTO. `bulkImportService` is the frontend consumer of the bulk import job API
(`/bulk-import`, `/bulk-import/{jobId}`). Before this slice every method
(`startImport`, `getJob`, `listJobs`, `cancelJob`) trusted the response body
shape and forwarded it directly to the typed return value, so an `index.html`
fallback (HTTP 200 with a string body) or a backend regression that dropped a
field would surface as a downstream type error rather than a clear
"unexpected response" failure.

## Backend Contract Evidence

`ecm-core/src/main/java/com/ecm/core/controller/BulkImportController.java`:

- Mounted at `/api/bulk-import` and `/api/v1/bulk-import` (the frontend
  `api.ts` base path adds the `/api/v1` prefix, so frontend relative paths
  remain `/bulk-import` and `/bulk-import/{jobId}`).
- `POST /bulk-import` (multipart) → `ResponseEntity<ImportJobDto>` (HTTP 202).
  Request parts: `files` (`MultipartFile[]`), `relativePaths`
  (`List<String>`, optional), `targetFolderId` (`UUID`, optional),
  `conflictPolicy` (`ConflictPolicy`, default `SKIP`).
- `GET /bulk-import/{jobId}` → `ResponseEntity<ImportJobDto>`.
- `GET /bulk-import` (pageable) → `ResponseEntity<Page<ImportJobDto>>`.
- `DELETE /bulk-import/{jobId}` → `ResponseEntity<ImportJobDto>`.

`ecm-core/src/main/java/com/ecm/core/service/BulkImportService.java`,
`ImportJobDto`:

```
ImportJobDto(UUID id, String userId, ImportJobStatus status,
             UUID targetFolderId, ConflictPolicy conflictPolicy,
             int totalFiles, int processedFiles, int importedFiles,
             int skippedFiles, int failedFiles,
             String currentItemPath, String lastMessage, String errorLog,
             LocalDateTime startedAt, LocalDateTime completedAt,
             LocalDateTime createdAt, LocalDateTime updatedAt)
```

`ecm-core/src/main/java/com/ecm/core/entity/ImportJob.java` confirms which
columns are `nullable = false`:

- Non-null on the wire: `id`, `userId`, `status`, `conflictPolicy`,
  `totalFiles`, `processedFiles`, `importedFiles`, `skippedFiles`,
  `failedFiles`, `createdAt`.
- Nullable on the wire: `targetFolderId`, `currentItemPath`, `lastMessage`,
  `errorLog`, `startedAt`, `completedAt`, `updatedAt`.

`ImportJobStatus` is the enum `PENDING | RUNNING | COMPLETED | FAILED |
CANCELED` and `ConflictPolicy` is the enum `SKIP | RENAME | OVERWRITE`. Both
serialize as their string names. The frontend mirrors both unions
verbatim in `bulkImportService.ts`.

For `listJobs`, Spring serializes a `Page<ImportJobDto>` so the JSON envelope
always carries `content`, `totalElements`, `totalPages`, `number`, and
`size`. The guard only enforces these five fields — other Spring page fields
(`first`, `last`, `sort`, `pageable`, `empty`) are ignored so backend
metadata changes do not break the boundary.

## Design

- Export `BULK_IMPORT_UNEXPECTED_RESPONSE_MESSAGE` — a stable, user-safe
  phrasing aligned with sibling guarded services.
- Preserve the public API surface: same four methods, same return types,
  same endpoint paths, same `FormData` construction (`files`,
  `relativePaths`, `targetFolderId`, `conflictPolicy`), same paging
  parameter shape (`{ params: { page, size } }`).
- Validate every response with a structural guard at the boundary, throwing
  `Error(BULK_IMPORT_UNEXPECTED_RESPONSE_MESSAGE)` on any mismatch.
- Convert each `api.get` / `api.delete` / `api.postFormData` call to
  `<unknown>` and assert the shape before returning to the caller.

## Files Changed

- `ecm-frontend/src/services/bulkImportService.ts`
- `ecm-frontend/src/services/bulkImportService.test.ts`
- `docs/BULK_IMPORT_SERVICE_SHAPE_GUARDS_DESIGN_VERIFICATION_20260515.md`

## Guard Rules

### `ImportJobDto`

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `id` | string | yes | UUID, serialized as string |
| `userId` | string | yes | |
| `status` | `ImportJobStatus` union | yes | `PENDING\|RUNNING\|COMPLETED\|FAILED\|CANCELED` |
| `conflictPolicy` | `ConflictPolicy` union | yes | `SKIP\|RENAME\|OVERWRITE` |
| `totalFiles` | finite number | yes | Backend `int`, never null |
| `processedFiles` | finite number | yes | Backend `int`, never null |
| `importedFiles` | finite number | yes | Backend `int`, never null |
| `skippedFiles` | finite number | yes | Backend `int`, never null |
| `failedFiles` | finite number | yes | Backend `int`, never null |
| `createdAt` | string | yes | ISO-8601 timestamp |
| `targetFolderId` | string \| null \| undefined | no | Nullable UUID |
| `currentItemPath` | string \| null \| undefined | no | Nullable |
| `lastMessage` | string \| null \| undefined | no | Nullable |
| `errorLog` | string \| null \| undefined | no | Nullable |
| `startedAt` | string \| null \| undefined | no | Nullable timestamp |
| `completedAt` | string \| null \| undefined | no | Nullable timestamp |
| `updatedAt` | string \| null \| undefined | no | Nullable timestamp |

### `ImportJobPage`

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `content` | `ImportJobDto[]` | yes | Every entry guarded |
| `totalElements` | finite number | yes | |
| `totalPages` | finite number | yes | |
| `number` | finite number | yes | |
| `size` | finite number | yes | |

Anything that fails any of the above (HTML fallback string, non-array
`content`, missing required field, wrong-typed nullable field, unsupported
`status` or `conflictPolicy` value, stringified numeric counter, non-string
timestamp) throws `Error(BULK_IMPORT_UNEXPECTED_RESPONSE_MESSAGE)`.

## Test Coverage

`ecm-frontend/src/services/bulkImportService.test.ts` mocks `./api`
(`get`, `delete`, `postFormData`) and asserts only observable behavior —
return values, mocked call arguments (URL, paging params), `FormData`
contents (`files`, `relativePaths`, `targetFolderId`, `conflictPolicy`),
and thrown error messages. No DOM or network access is performed.

- `startImport`:
  - Success path forwards `/bulk-import` and the `FormData` carries the
    expected `files` (instance preserved), `relativePaths`, explicit
    `targetFolderId`, and explicit `conflictPolicy`.
  - When `targetFolderId` is omitted and `conflictPolicy` defaulted, the
    `FormData` has no `targetFolderId` entry and `conflictPolicy=SKIP`.
  - HTML fallback rejected.
  - Stringified `importedFiles` rejected (covers missing numeric counters).
  - Status outside the closed union rejected.
  - `conflictPolicy` outside the closed union rejected.
- `getJob`:
  - Success path forwards `/bulk-import/{jobId}`.
  - Nullable optional fields (missing/null timestamps and folder id)
    accepted via the `nullableJob` fixture.
  - Non-string `createdAt` rejected.
  - HTML fallback rejected.
- `listJobs`:
  - Explicit `(2, 5)` forwards `/bulk-import` with the paging params.
  - Default `()` forwards `{ page: 0, size: 20 }`.
  - String `totalElements` rejected (page envelope guard).
  - Page entry with a wrong-typed nullable field (`currentItemPath`)
    rejected (covers malformed page item).
  - Page entry with invalid status enum rejected.
  - HTML fallback rejected.
- `cancelJob`:
  - Success path forwards DELETE `/bulk-import/{jobId}` and returns the
    guarded job (here transitioned to `CANCELED`).
  - HTML fallback rejected.
  - Non-string `userId` rejected.

## Verification

### Targeted Service Test

Intended command:

```bash
cd ecm-frontend
CI=true npm test -- --runTestsByPath src/services/bulkImportService.test.ts --watchAll=false
```

Result: **Not run locally.** The Claude worktree had no local
`node_modules` and the operator did not grant permission to reuse the main
worktree's cache via symlink, so the targeted test could not be executed
inside this worktree. The Codex-side gate (or a subsequent verification
pass with `node_modules` available) is expected to run this.

Codex follow-up verification:

```bash
cd ecm-frontend
npm test -- --runTestsByPath src/services/bulkImportService.test.ts --watchAll=false
```

Result: PASS. 1 test suite, 19 tests, 0 failures.

### Full Frontend Gates

Intended commands:

```bash
cd ecm-frontend
npm run lint
CI=true npm run build
```

Result: **Not run locally**, same reason as above (no `node_modules`
in the worktree).

Codex follow-up verification:

```bash
cd ecm-frontend
npm run lint
CI=true npm run build
```

Result: PASS. `CI=true npm run build` emitted the existing CRA bundle-size
advisory.

### Remote CI

Not triggered. This slice is committed in the worktree only and not pushed,
per the task scope.

## Residual Work

- This slice does not add new bulk-import product capability.
- Other frontend services may still need equivalent response-shape guards
  (the broader hardening line is ongoing).
- Local lint, type, and Jest verification should run before this slice is
  merged.
