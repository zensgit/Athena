# Bulk Metadata Service Shape Guards: Design and Verification

Date: 2026-05-15

## Scope

This slice hardens `ecm-frontend/src/services/bulkMetadataService.ts` against
malformed runtime responses while preserving the existing frontend public API
and backend route contract.

No backend code, route contract, or request payload shape was changed.

## Backend Contract

Backend controller:

- `BulkOperationController`
- Mount: `/api/v1/bulk`

Frontend relative path remains unchanged:

- `POST /bulk/metadata`

Backend DTOs checked:

- `BulkMetadataService.BulkMetadataRequest`
- `BulkMetadataService.BulkMetadataResult`

The response shape is:

- `operation: string`
- `totalRequested: number`
- `successCount: number`
- `failureCount: number`
- `successfulIds: string[]`
- `failures: Record<string, string>`

## Design

Added `BULK_METADATA_UNEXPECTED_RESPONSE_MESSAGE` and a runtime response guard.

The service now reads the `api.post` result as `unknown`, verifies the response
shape, and only then returns `BulkMetadataResult`.

The guard rejects:

- SPA HTML fallback strings.
- Missing or non-string `operation`.
- Non-number count fields.
- Non-string entries in `successfulIds`.
- Non-object or non-string-valued `failures`.

## Verification

Targeted Jest:

```bash
cd ecm-frontend
npm test -- --runTestsByPath src/services/bulkMetadataService.test.ts --watchAll=false
```

Result: PASS. 1 test suite, 6 tests, 0 failures.

Covered cases:

- Success path forwards `POST /bulk/metadata` and preserves the payload.
- Empty success result accepted.
- HTML fallback rejected.
- Malformed numeric counters rejected.
- Malformed `successfulIds` array rejected.
- Malformed `failures` map rejected.

## Commit

`0d6226c fix(bulk-metadata): guard service responses`

## Notes

This slice is intentionally frontend-only. It composes with the parallel
`blogService` guard slice before combined lint/build/CI verification.
