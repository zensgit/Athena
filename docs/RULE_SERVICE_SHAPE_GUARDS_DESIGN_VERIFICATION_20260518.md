# Rule Service Shape Guards: Design and Verification

Date: 2026-05-18

## Scope

This slice continues the frontend service response-shape guard track for
`ecm-frontend/src/services/ruleService.ts`.

The goal is to fail fast when the API client receives an HTML fallback or
malformed JSON shape that mocked frontend tests may not catch. This is a
frontend-service-only change.

Out of scope:

- Backend controllers, endpoint paths, request payloads, and query params.
- CSV export/download methods, which still use `api.downloadFile`.
- Void write methods such as delete and manual scheduled-rule trigger.
- Package files, migrations, and `.env`.

`.env` was already modified before this work and was not touched, staged, or
committed.

## Design

`ruleService` now exports:

- `RULE_UNEXPECTED_RESPONSE_MESSAGE`

The service now validates JSON responses for these contract families:

- paged rule lists: all rules, my rules, search, folder-scoped rules
- rule entity reads/writes: get, create, update, enable, disable
- folder scoped operations: reorder and dry-run
- manual execution ledger: execute, list, timeline, get run
- rule testing and condition validation
- templates and action definitions
- aggregate and per-rule stats
- audit timeline list
- cron validation

The guards are intentionally structural rather than business-exhaustive. They
pin the fields that the frontend depends on and reject common broken responses:

- raw HTML strings from missing/misrouted endpoints
- objects missing required arrays such as `content`, `actions`, or `results`
- nested ledger/action records missing required booleans or counters
- malformed `valid` responses from validation endpoints

## Parallel Development

This local slice owns only:

- `ecm-frontend/src/services/ruleService.ts`
- `ecm-frontend/src/services/ruleService.test.ts`
- this document

The planned parallel `workflowService` slice was first attempted with Claude
Code CLI in a separate worktree, but the CLI returned a quota-limit message
before producing changes. A Codex worker fallback was started in the same
separate worktree so this `ruleService` work could continue without file
overlap.

## Verification

Targeted Jest:

```bash
cd ecm-frontend
npm test -- --runTestsByPath src/services/ruleService.test.ts --watchAll=false
```

Result:

```text
PASS src/services/ruleService.test.ts
Test Suites: 1 passed, 1 total
Tests:       5 passed, 5 total
```

The targeted tests cover:

- successful guarded responses and endpoint preservation for paged lists
- rule CRUD and enable/disable paths
- folder reorder/dry-run, manual execution, timeline, and get-run paths
- metadata, stats, validation, cron, audit, and CSV export path preservation
- representative HTML fallback and malformed nested response rejection

Broader frontend verification and CI status are recorded in the integration
document for the combined round.
