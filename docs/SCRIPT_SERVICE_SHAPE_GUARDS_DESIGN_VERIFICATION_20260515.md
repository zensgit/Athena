# Script Service Shape Guards: Design and Verification

Date: 2026-05-15

## Scope

This slice hardens `ecm-frontend/src/services/scriptService.ts` against malformed
runtime responses while preserving its existing public API and endpoint paths.

No backend code, route contract, or request payload shape was changed.

## Backend Contract

Backend controller:

- `ScriptController`
- Mounts: `/api/scripts` and `/api/v1/scripts`

Frontend relative paths remain unchanged:

- `GET /scripts`
- `GET /scripts/{scriptId}`
- `POST /scripts`
- `PUT /scripts/{scriptId}`
- `DELETE /scripts/{scriptId}`
- `POST /scripts/execute`

Backend DTO records checked:

- `ScriptService.ScriptDefinitionDto`
- `ScriptService.ScriptExecutionResult`

## Design

Added `SCRIPT_UNEXPECTED_RESPONSE_MESSAGE` and runtime guards for service
readbacks:

- Script definition list and item responses must be arrays/objects with the
  expected string, boolean, nullable string, and string-array fields.
- Script execution responses must include a `result` key, string-array `logs`,
  nullable `scriptPath`, boolean `storedScript`, numeric `durationMs`, and
  string `executedAt`.
- `result` intentionally accepts any value, including `null`, because the
  backend record exposes it as `Object`.
- `DELETE /scripts/{scriptId}` remains unguarded because the backend returns
  `204 No Content`.

All guarded calls now read `unknown` from `api` and assert the runtime shape
before returning the typed value.

## Verification

Targeted Jest:

```bash
cd ecm-frontend
npm test -- --runTestsByPath src/services/scriptService.test.ts --watchAll=false
```

Result: PASS. 1 test suite, 12 tests, 0 failures.

Covered cases:

- List/detail/create/update/execute success paths and endpoint forwarding.
- HTML fallback rejection.
- Malformed script list items.
- Malformed script detail boolean fields.
- Delete no-content behavior.
- Inline execution result with `null` result and `null` scriptPath.
- Execution result missing the required `result` key.
- Execution result with malformed logs.

## Commit

Pending integration commit at the time this document was written.

## Notes

This slice is intentionally frontend-only. It closes one small service guard
gap and is designed to compose with a parallel `bulkImportService` guard slice
before the combined lint/build/CI verification.
