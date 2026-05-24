# Bulk Record Declaration — Verification

Companion to `docs/BULK_RECORD_DECLARATION_ADJUDICATION_AND_DESIGN_20260524.md`.

Date: 2026-05-24
Brief revisions accepted by gate: v1 → v2 (3 findings) → v3 (3 findings) → v3.1 (1 non-blocking note).

## Adjudication outcome (brief §2)

Gate verdict: **bulk record declaration is OUTSIDE the `project_rm_preset_delivery_closeout` polish zone.** PR-122 closed the `RmReportPreset*` subsurface (`RmReportPresetController`, `RmReportPresetService`, `RmReportPresetDeliveryService`, `RmReportPreset` / `RmReportPresetExecution` entities and repositories, plus the frontend preset-delivery section of `RecordsManagementPage.tsx`). The declaration subsurface — `RecordsManagementService.declareRecord` / `assignRecordCategory` and the `RecordsManagementController` declare routes — was never named by the closeout doc.

The slice's no-touch ring on the preset-delivery files was enforced verbatim: see §"Files changed" below — every modified backend file is `RecordsManagement*`, and every new/modified frontend file is on the declaration/category path or in `src/components/records/` outside any preset-delivery name.

## Production changes

### Backend

| File | Change |
|---|---|
| `ecm-core/src/main/java/com/ecm/core/service/RecordsManagementService.java` | +1 final field `PlatformTransactionManager transactionManager`. Imported `org.springframework.transaction.{PlatformTransactionManager, TransactionDefinition}`, `org.springframework.transaction.annotation.Propagation`, `org.springframework.transaction.support.TransactionTemplate`. Refactored `declareRecord` (`:501-543`) to extract shared private helper `declareDocumentInternal(Document, String, UUID)` — the existing pre-flight guards stay in `declareRecord`; the body after the already-declared early-return calls the helper. Added bulk surface: orchestrator `declareRecordsBulk(BulkDeclareRequest)` annotated `@Transactional(propagation = Propagation.NOT_SUPPORTED)` with inline per-call `TransactionTemplate` (`PROPAGATION_REQUIRES_NEW`); private helper `declareOneRow(UUID, UUID, String) → DeclareOneOutcome`; private exceptions `NodeNotFoundForDeclareException` / `NodeNotVisibleForDeclareException` for typed error routing; private record `DeclareOneOutcome(Status, RecordDeclarationDto)` with enum `Status { DECLARED, SKIPPED_ALREADY_DECLARED }` (in-band signal between helper and orchestrator — Finding 1 / v3 Blocker resolution); public enums `BulkDeclareStatus` and `BulkDeclareErrorCategory`; public records `BulkDeclareRequest`, `BulkDeclareResult` (with `declared` / `skippedAlreadyDeclared` / `failed` factories), `BulkDeclareResults`, `BulkDeclareResponse`. INTERNAL_ERROR sanitisation: `errorMessage` carries fixed copy + `ex.getClass().getSimpleName()` only; `log.debug` records only `nodeId` + class name; raw `Throwable` is never passed to SLF4J. |
| `ecm-core/src/main/java/com/ecm/core/controller/RecordsManagementController.java` | Added `POST /api/v1/nodes/bulk-declare` returning `BulkDeclareResponse`. Class-level `@PreAuthorize("hasRole('ADMIN')")` (`:40`) already gates the endpoint. |

### Frontend

| File | Change |
|---|---|
| `ecm-frontend/src/types/index.ts` | Added `BulkDeclareStatus` (union), `BulkDeclareErrorCategory` (union of `NODE_NOT_FOUND` / `NODE_NOT_VISIBLE` / `INTERNAL_ERROR`), `BulkDeclareRequest`, `BulkDeclareResult`, `BulkDeclareResults`, `BulkDeclareResponse`. |
| `ecm-frontend/src/services/recordsManagementService.ts` | Imported new types. Added dedicated sentinel `RECORDS_MANAGEMENT_BULK_DECLARE_UNEXPECTED_RESPONSE_MESSAGE` (separate from the existing RM sentinel so Phase 5 Mocked HTML-fallback drift on the new route is grep-able). Added predicates: `isBulkDeclareStatus`, `isBulkDeclareErrorCategory`, `isBulkDeclareResult` (Finding 3 row-shape invariants), `assertBulkDeclareResponse` (top-level wrapper guard). Added method `createBulkDeclarations(request)` calling `POST /nodes/bulk-declare`. |
| `ecm-frontend/src/components/records/BulkDeclareRecordsDialog.tsx` *(new)* | Dialog component with paste-N-UUIDs textarea, category dropdown (reuses `listRecordCategories`), optional comment field, submit gated on parsed-count > 0, aggregate toast on full success, partial-failure flow that drains successful UUIDs from the textarea + renders per-`errorCategory` failed-rows Alert + dedicated soft-skip Alert for SKIPPED_ALREADY_DECLARED. Exports `parseUuidList` (mirrors `LegalHoldsPage.tsx`'s 8-4-4-4-12 generic UUID parser). |
| `ecm-frontend/src/pages/RecordsManagementPage.tsx` | Added `BulkDeclareRecordsDialog` import + `bulkDeclareDialogOpen` state + "Bulk Declare Records" button in the page header (next to Refresh) + dialog instance wired through `categories` prop and `onDeclared = void loadAdminData(true)`. The preset-delivery section was not touched. |

## Tests added

### Backend (Java)

`ecm-core/src/test/java/com/ecm/core/service/RecordsManagementServiceTest.java` — 11 new tests in a dedicated bulk-declare block:

1. `declareRecordsBulkAllDeclared` — 3 distinct DECLARED rows, verifies per-row TransactionTemplate engagement (`times(3)`) and `documentRepository.save` × 3.
2. `declareRecordsBulkAlreadyDeclaredSkipped` — already-declared row ends `SKIPPED_ALREADY_DECLARED` with non-null `declaration`, `errorCategory == null`, no save.
3. `declareRecordsBulkAlreadyDeclaredWithCategoryRequestStillSkipsCategoryAssignment` *(Finding 2 lock)* — non-null `request.categoryId` on an already-declared row: still `SKIPPED_ALREADY_DECLARED`, `categoryRepository.findById` never invoked, no save, no `RM_RECORD_CATEGORY_ASSIGNED` audit.
4. `declareRecordsBulkMissingNodeFailedPartial` — `[ok, missing, ok]` → DECLARED / FAILED(NODE_NOT_FOUND) / DECLARED, 2 saves.
5. `declareRecordsBulkInvisibleNodeReportsCategory` — tenant-invisible node maps to `NODE_NOT_VISIBLE`.
6. `declareRecordsBulkInternalErrorSanitisedMessage` — RuntimeException("BULK_DECLARE_USER_PII_PROBE_d8c4a2f0") on save: response `errorMessage` does NOT contain the probe; DOES contain `RuntimeException`.
7. `declareRecordsBulkContinuesAfterFailedRowAndPreservesOrder` *(Finding 1 lock — three independent locks)* — `[ok1, fail2, ok3]` → asserts `getTransaction` × 3, result rows in input order, `documentRepository.save` × 2 (only DECLARED rows save).
8. `declareRecordsBulkEmptyNodeIdsReturns400` — empty list throws `IllegalArgumentException`; no `getTransaction`, no save.
9. `declareRecordsBulkNullOnlyNodeIdsReturns400` *(v3.1 lock)* — `[null, null]` throws `IllegalArgumentException` with "non-null" in the message; post-dedupe guard verified.
10. `declareRecordsBulkRejectsNonAdmin` — `hasRole("ROLE_ADMIN")` returns false: `SecurityException`, no `findById`, no save.
11. `declareRecordsBulkDedupesDuplicateNodeIdsInRequest` — same UUID three times → 1 result row, 1 transaction, 1 save (consistent with legal-hold + frontend parser).

`ecm-core/src/test/java/com/ecm/core/controller/RecordsManagementControllerTest.java` — 4 new tests:

1. `declareRecordsBulkReturnsMixedRowsPayload` — locks JSON shape `$.bulkDeclareResults.rows[*]` with DECLARED + SKIPPED_ALREADY_DECLARED + FAILED variants and asserts `errorCategory` field absence on success rows.
2. `declareRecordsBulkEmptyArrayReturns400` — empty `nodeIds: []` mapped to 400 via `RestExceptionHandler`.
3. `declareRecordsBulkNullOnlyArrayReturns400` — `[null, null]` mapped to 400 (v3.1).
4. `declareRecordsBulkPartialFailureReturns200` — partial failure is HTTP 200 with the row-level `errorCategory` (not a top-level error).

`ecm-core/src/test/java/com/ecm/core/controller/RecordsManagementControllerSecurityTest.java` — extended:

- `nonAdminUsersCannotAccessEndpoints` — added 403 case for `POST /api/v1/nodes/bulk-declare` under `@WithMockUser(roles = "USER")`.
- `adminsCanAccessEndpoints` — added 200 case for the same endpoint under `@WithMockUser(roles = "ADMIN")` with a stubbed empty-row response.
- Top-level `endpointsRequireAuthentication` (existing) covers the 401 case for any `/api/**` route including bulk-declare.

### Frontend (Jest / RTL)

`ecm-frontend/src/services/recordsManagementService.test.ts` — 9 new tests in `describe('createBulkDeclarations')`:

- happy path mixed rows: DECLARED + SKIPPED_ALREADY_DECLARED + FAILED parsed via the wrapper; payload shape verified.
- payload trims `comment` whitespace and omits empty `categoryId`.
- HTML fallback throws the dedicated sentinel (distinct from the generic RM sentinel).
- Shape-drift rejection: DECLARED + populated errorMessage; SKIPPED_ALREADY_DECLARED + populated errorCategory; **FAILED + errorCategory='ALREADY_DECLARED'** (Finding 3 lock — there is no such error category).
- Unknown errorCategory variant rejected.
- FAILED + empty errorMessage rejected.
- Top-level `bulkDeclareResults` wrapper missing → rejected.
- DECLARED with explicit JSON `null` errorCategory + null errorMessage accepted (wire-shape tolerance per `feedback_guard_predicates_real_backend_shape_drift`).

`ecm-frontend/src/components/records/BulkDeclareRecordsDialog.test.tsx` *(new)* — 11 tests:

`parseUuidList` (5 unit tests):

- empty / whitespace-only → `[]`
- dedupes preserving first-seen order
- accepts non-v4 UUID variants (v1 + v5) — Finding 2 lock
- drops blanks, malformed, non-UUID tokens
- splits on commas and semicolons in addition to newlines

Dialog flow (6 tests):

- submit disabled when 0 valid UUIDs parsed (including when only malformed tokens are typed)
- all-DECLARED closes + emits success toast + `onDeclared` called
- partial-failure stays open, drains successful UUIDs from the textarea (leaves failed UUIDs in place), renders `data-testid="bulk-declare-failed-rows"` with `bulk-declare-failed-node_not_found` and `bulk-declare-failed-internal_error` sub-groups, asserts INTERNAL_ERROR message includes `RuntimeException` but not the probe text
- SKIPPED_ALREADY_DECLARED rows render in `data-testid="bulk-declare-skipped-rows"` Alert, separate from the failed-rows Alert (lock: SKIPPED is not under any errorCategory heading)
- comment trim is delegated to the service layer (dialog forwards raw textarea value; service trims)
- service rejection (HTML fallback / network error) emits toast.error and leaves submit re-enabled

## Local verification

```bash
cd ecm-frontend
CI=true npm test -- --runTestsByPath \
  src/services/recordsManagementService.test.ts \
  src/components/records/BulkDeclareRecordsDialog.test.tsx \
  --watchAll=false
# Test Suites: 2 passed, 2 total
# Tests:       73 passed, 73 total

CI=true npm test -- --runTestsByPath \
  src/components/records/DeclareRecordDialog.test.tsx \
  src/components/records/UndeclareRecordDialog.test.tsx \
  src/pages/RecordsManagementPage.test.tsx \
  --watchAll=false
# 84 tests passed (existing dialog + page tests untouched)

npm run lint
# clean

CI=true npm run build
# build successful

git diff --check -- . ':!.env'
# clean
```

Backend `mvn` is Docker-gated locally per CLAUDE.md (`./mvnw` wraps a Maven Docker image and Docker is not available on the dev box); CI is the gate.

## Implementation diffs from brief

The implementation matches the brief except where noted:

| Brief section | Notes |
|---|---|
| §5 page-test surface | The brief called for `RecordsManagementPage.test.tsx` to receive the new tests. Implementation places them in a dedicated `components/records/BulkDeclareRecordsDialog.test.tsx` file matching the existing codebase pattern (`DeclareRecordDialog.test.tsx`, `UndeclareRecordDialog.test.tsx`). The dialog is tested in isolation; the page integration is minimal (button + state + dialog mount). Sibling dialog tests stay green (verified above). |
| §4 service method outline `requireRecordsAdminOrAuthorizedCaller()` | Implemented as `requireAdmin()` — matches the actual existing helper at `RecordsManagementService.java:3465+`. |
| §4 nested-record naming | The brief mentioned both `BulkDeclareResults` and `BulkDeclareResponse` as separate types. Implementation keeps both — `BulkDeclareResults { rows }` is the inner wrapper, `BulkDeclareResponse { bulkDeclareResults }` is the top-level envelope. JSON shape: `$.bulkDeclareResults.rows[*]`. |
| §4 parseUuidList placement | The brief considered extracting `parseUuidList` to a shared utility. Implementation duplicates the parser inside `BulkDeclareRecordsDialog.tsx` (mirrors `LegalHoldsPage.tsx`'s definition byte-for-byte) to keep the dialog self-contained and avoid component-from-page imports. If a third caller appears, lift to `src/utils/uuid.ts`. |

## CI Follow-Up

To be filled after `git push` triggers the 7-job gate.

```
Run id:        <pending>
Head SHA:      <pending>
Conclusion:    <pending>
Jobs:          Backend Verify, Frontend Build & Test, Phase C Security,
               Acceptance Smoke, Property Encryption Closeout,
               Phase 5 Mocked Regression, Frontend E2E Core
```
