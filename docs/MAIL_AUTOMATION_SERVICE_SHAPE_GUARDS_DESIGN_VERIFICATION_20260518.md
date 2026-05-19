# Mail Automation Service Shape Guards: Design and Verification

Date: 2026-05-18

## Scope

This round extends the frontend service response-shape guard track to
`mailAutomationService`, following the patterns established for
`ruleService` and `workflowService`.

The intent is defensive hardening against HTML fallback or malformed API
responses that Phase 5 Mocked frontend tests may otherwise miss
(see `feedback_phase5_mocked_html_fallback.md`).

This round did not change backend controllers, backend contracts, endpoint
paths, request payloads, query params, Blob/download methods, void methods,
package files, migrations, pages, e2e tests, unrelated services, or `.env`.

## Files Touched

Write set:

- `ecm-frontend/src/services/mailAutomationService.ts`
- `ecm-frontend/src/services/mailAutomationService.test.ts` (new)
- `docs/MAIL_AUTOMATION_SERVICE_SHAPE_GUARDS_DESIGN_VERIFICATION_20260518.md`
  (this file)

No other files were modified.

## Guard Coverage

`mailAutomationService` now guards JSON responses for:

- account CRUD and OAuth reset: `listAccounts`, `createAccount`,
  `updateAccount`, `resetOAuth`
- account connectivity: `testConnection`, `getOAuthAuthorizeUrl`, `listFolders`
- rule CRUD: `listRules`, `createRule`, `updateRule`
- diagnostics, report, and report scheduling:
  `getDiagnostics`, `getReport`, `getReportSchedule`, `runReportScheduleNow`
- processed-mail mutation and readbacks:
  `bulkDeleteProcessedMail`, `replayProcessedMail`,
  `listProcessedMailDocuments`
- retention, runtime metrics, fetch lifecycle, and rule debug:
  `getProcessedRetention`, `cleanupProcessedRetention`, `getRuntimeMetrics`,
  `triggerFetch`, `getFetchSummary`, `triggerFetchDebug`, `previewRule`
- provider presets: `listProviderPresets` (preserves the existing
  empty-array fallback so the Custom option still renders when the route
  returns SPA HTML or a malformed list)
- SMTP runtime probe: `testSmtp` (retains the dedicated
  `TEST_SMTP_UNEXPECTED_RESPONSE_MESSAGE` sentinel and shape semantics)

Two sentinel constants are exported:

- `MAIL_AUTOMATION_UNEXPECTED_RESPONSE_MESSAGE` — new general sentinel for
  the JSON-returning methods listed above.
- `TEST_SMTP_UNEXPECTED_RESPONSE_MESSAGE` — preserved verbatim for
  `testSmtp` so existing operator-facing UI strings and any external
  references continue to match.

Out of scope and intentionally unchanged:

- Blob endpoints: `exportReportCsv`, `exportDiagnosticsCsv` (use
  `api.getBlob`, no JSON body to validate).
- Void endpoints: `deleteAccount`, `deleteRule` (no body returned).
- Endpoint paths, query parameters, and request bodies — every call is
  byte-for-byte unchanged.

## Implementation Notes

- All guarded methods switched from `api.get<T>` / `api.post<T>` style to
  `api.*<unknown>` followed by a `is*` narrowing predicate, mirroring
  `ruleService.ts`.
- A small helper bundle (`isObject`, `isFiniteNumber`,
  `isStringOrNullish`, `isBooleanOrNullish`, `isNumberOrNullish`,
  `isStringArray`, `isNumberRecord`) supports both flat and nested type
  guards.
- `assertMailResponse`, `assertMailArray`, and `assertMailStringArray`
  throw the general sentinel; the previously inline TEST_SMTP guard still
  throws the dedicated SMTP sentinel.
- `listProviderPresets` returns `[]` (not throws) on either non-array
  responses or arrays with malformed entries, preserving the prior page
  behavior whereby the Custom option remains available when the route is
  unmocked.

## Verification

Targeted Jest:

```bash
cd ecm-frontend
npm test -- --runTestsByPath src/services/mailAutomationService.test.ts --watchAll=false
```

Result:

```text
PASS src/services/mailAutomationService.test.ts
  mailAutomationService response shape guards
    ✓ guards account CRUD, OAuth reset, and connection test responses
    ✓ guards rule CRUD, OAuth authorize URL, folder list, and preview responses
    ✓ guards diagnostics, report, schedule, retention, and runtime metrics responses
    ✓ returns processed mail documents list and preserves the limit param
    ✓ falls back to an empty preset list on HTML fallback but returns the array when valid
    ✓ guards testSmtp with the dedicated TEST_SMTP_UNEXPECTED_RESPONSE_MESSAGE sentinel
    ✓ rejects HTML fallback and malformed JSON for representative endpoints

Test Suites: 1 passed, 1 total
Tests:       7 passed, 7 total
```

Frontend lint:

```bash
cd ecm-frontend
npm run lint
```

Result: PASS (no warnings or errors).

Notes:

- `node_modules` for the worktree was hydrated from the npm cache via
  `npm ci --prefer-offline` (cache was warm; install completed in ~8s).
  See `feedback_parallel_worktree_cold_cache_stall.md` for context on why
  cold installs are avoided.
- Backend CI build, mocked regression gate, and Frontend E2E gate were not
  re-run in this round; they are unaffected because no backend code,
  pages, e2e tests, or `.env` were touched. The pushed CI run on the
  branch is expected to mirror the green sibling guard rounds.

## Follow-Up

Follow-up from the parent integration round:

- `recordsManagementService` was completed in the same parent round. See
  `SERVICE_GUARDS_RECORDS_MAIL_INTEGRATION_VERIFICATION_20260518.md`.
- Remaining candidates are `nodeService` split into smaller slices,
  `opsRecoveryService` async-export tail, `bulkOperationService`, and
  `tagService`.
