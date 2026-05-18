# Content Archive Service Shape Guards: Design and Verification

Date: 2026-05-17

## Scope

This slice hardens `ecm-frontend/src/services/contentArchiveService.ts`
against malformed runtime responses while preserving existing endpoint
paths, request payloads, default values, and no-body calls.

No backend code, route contract, or UI behavior was changed.

## Backend Contract

Backend sources checked:

- `ContentArchiveController`
- `ContentArchiveService`
- `ArchivePolicyService`
- `Node.ArchiveStatus`
- `Node.ArchiveStoreTier`

Frontend relative paths remain unchanged:

- `POST /nodes/{nodeId}/archive`
- `POST /nodes/{nodeId}/restore`
- `GET /nodes/{nodeId}/archive-status`
- `GET /nodes/archived`
- `GET /folders/{folderId}/archive-policy`
- `PUT /folders/{folderId}/archive-policy`
- `DELETE /folders/{folderId}/archive-policy`
- `POST /folders/{folderId}/archive-policy/dry-run`
- `POST /folders/{folderId}/archive-policy/execute`
- `GET /archive-policies`
- `POST /archive-policies/run`

Closed enum values:

- `ArchiveStatus`: `LIVE | ARCHIVED | RESTORING`
- `ArchiveStoreTier`: `HOT | WARM | COLD | GLACIER`

## Design

Added `CONTENT_ARCHIVE_UNEXPECTED_RESPONSE_MESSAGE` and runtime guards
for every JSON response body returned by `contentArchiveService`.

Guarded shapes:

- Mutation/status DTOs require node identity fields, closed archive
  status/tier enums, nullable archive metadata, and numeric affected
  counts where applicable.
- Archived-node pages require a Spring page envelope and guarded
  `content` entries.
- Archive policies require folder identity fields, boolean policy flags,
  finite numeric policy parameters, closed storage tiers, and nullable
  last-run metadata.
- Dry-run responses require folder identity fields, cutoff date, closed
  storage tier, finite counts, and guarded candidate arrays.
- Execution and batch responses require finite counts, `string[]`
  failure arrays, nullable error strings, and guarded nested results.

`deleteArchivePolicy` remains a no-content endpoint and is intentionally
not guarded. Existing no-body `POST` calls for execute/run were preserved.

## Test Coverage

Updated test file:

- `ecm-frontend/src/services/contentArchiveService.test.ts`

Covered cases:

- Archive and restore endpoint forwarding.
- HTML fallback rejection for mutation responses.
- Invalid archive status enum rejection.
- Archive status response guard.
- Archived-node page params and malformed page item rejection.
- Get/upsert policy endpoint forwarding.
- Malformed policy rejection.
- Delete remains no-content.
- Dry-run default `{}` payload and malformed candidate rejection.
- Execute preserves existing no-body call and rejects malformed failures.
- List/run archive policies and malformed nested batch rejection.

## Verification

Targeted Jest:

```bash
cd ecm-frontend
npm test -- --runTestsByPath src/services/contentArchiveService.test.ts --watchAll=false
```

Result:

```text
PASS src/services/contentArchiveService.test.ts
Test Suites: 1 passed, 1 total
Tests:       16 passed, 16 total
```

Frontend lint:

```bash
cd ecm-frontend
npm run lint
```

Result: PASS.

Diff hygiene:

```bash
git diff --check
```

Result: PASS.

## Commit

Pending commit at document write time:

- `fix(content-archive): guard service responses`

## Notes

`.env` was already modified before this slice and was not touched,
staged, or committed.

The parallel Claude `opsRecoveryService` worktree was still in progress
when this document was first written; that larger slice is tracked
separately.
