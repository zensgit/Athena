# Ops Policy Service Shape Guards: Design and Verification

Date: 2026-05-15

## Scope

This slice hardens `ecm-frontend/src/services/opsPolicyService.ts`
against malformed runtime responses while preserving the existing
frontend service API, endpoint paths, request payloads, defaults, and
domain encoding behavior.

No backend code, controller path, or UI behavior was changed.

## Backend Contract

Backend controller:

- `OpsPolicyController`

Controller mount:

- `/api/v1/ops/policies`

Frontend relative paths remain unchanged:

- `GET /ops/policies` with params `{ domain }`
- `GET /ops/policies/{domain}/history` with params `{ limit }`
- `PUT /ops/policies/{domain}`
- `POST /ops/policies/{domain}/rollback`

Defaults remain unchanged:

- `getDomain(domain = 'PREVIEW')`
- `getHistory(domain = 'PREVIEW', limit = 20)`
- `rollback(domain, payload ?? {})`

Backend records checked:

- `OpsPolicyDomainStateDto`
- `OpsPolicyProfileDto`
- `OpsPolicyUpdateResponseDto`
- `OpsPolicyRollbackResponseDto`
- `OpsPolicyHistoryEntryDto`
- `OpsPolicyHistoryResponseDto`

## Design

Added `OPS_POLICY_UNEXPECTED_RESPONSE_MESSAGE` and runtime guards for
all JSON response bodies returned by `opsPolicyService`.

Guarded shapes:

- `OpsPolicyProfile`: string `key`, string `label`, finite numeric
  retry fields, boolean `builtIn`.
- `OpsPolicyDomainState`: string `domain`, finite numeric
  `currentVersion`, nullable string `updatedAt`, nullable string
  `actor`, nullable string `reason`, guarded `policies`.
- `OpsPolicyUpdateResponse`: domain/version/audit fields, nullable
  `updatedPolicy`, guarded `policies`, optional nullable string `error`.
- `OpsPolicyRollbackResponse`: previous/rolled-back/current versions,
  nullable audit fields, guarded `policies`, optional nullable string
  `error`.
- `OpsPolicyHistoryEntry`: finite numeric `version`, nullable audit
  fields.
- `OpsPolicyHistoryResponse`: string `domain`, finite numeric
  `currentVersion`, guarded `history`.

All `api.get`, `api.put`, and `api.post` calls now request `unknown` and
assert the response body before returning typed data. HTML fallback
strings and malformed JSON now fail at the service boundary with the
exported unexpected-response message.

## Test Coverage

New test file:

- `ecm-frontend/src/services/opsPolicyService.test.ts`

Covered cases:

- `getDomain` forwards default `PREVIEW` params and returns guarded
  state.
- `getDomain` rejects HTML fallback.
- `getDomain` rejects malformed nested policy profiles.
- `updatePolicy` encodes domains, forwards payloads, and guards the
  response.
- `updatePolicy` accepts `updatedPolicy: null` and `error: string|null`.
- `updatePolicy` accepts omitted optional `error`.
- `updatePolicy` rejects malformed `updatedPolicy`.
- `rollback` encodes domains, defaults payload to `{}`, and guards the
  response.
- `rollback` forwards explicit payloads.
- `rollback` rejects malformed version fields.
- `getHistory` forwards default and custom params.
- `getHistory` rejects malformed history entries.

## Verification

Targeted Jest:

```bash
cd ecm-frontend
npm test -- --runTestsByPath src/services/opsPolicyService.test.ts --watchAll=false
```

Result:

```text
PASS src/services/opsPolicyService.test.ts
Test Suites: 1 passed, 1 total
Tests:       13 passed, 13 total
```

Frontend lint:

```bash
cd ecm-frontend
npm run lint
```

Result: PASS.

Frontend production build:

```bash
cd ecm-frontend
CI=true npm run build
```

Result: PASS. The build emitted the existing CRA bundle-size advisory
and Node `fs.F_OK` deprecation warning, but compilation succeeded.

Diff hygiene:

```bash
git diff --check
```

Result: PASS.

## Commit

- `a86a144 fix(ops-policy): guard service responses`

## Remote CI

Run: `25908367326`

Head: `a86a144f5a895fcaed4c9d5057f0d84a03dbe818`

Result: PASS.

Jobs:

- Backend Verify: success.
- Frontend Build & Test: success.
- Phase C Security Verification: success.
- Property Encryption Closeout Gate: success.
- Phase 5 Mocked Regression Gate: success.
- Frontend E2E Core Gate: success.
- Acceptance Smoke (3 admin pages): success.

## Notes

`.env` was already modified before this slice and was not touched,
staged, or committed.

The earlier Claude worktree attempt did not produce file changes, so
Codex implemented the same approved plan directly in the main worktree.
