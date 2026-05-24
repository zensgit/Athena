# Legal-Hold Bulk-Apply + Structured Release-Reason — Verification

Date: 2026-05-24

## Context

Implementation of the slice scoped in `docs/LEGAL_HOLD_BULK_APPLY_AND_RELEASE_REASON_DESIGN_20260524.md`. All three gate-applied corrections (orchestration pattern over outer-transaction; generic-UUID over v4-only; wrapper `BulkApplyResults`) and the doc-trail copy fix are reflected in production code.

## Production changes

### Backend (Java + Liquibase)

| File | Change |
|---|---|
| `ecm-core/src/main/resources/db/changelog/changes/094-add-legal-hold-release-reason.xml` (new) | Adds nullable `legal_holds.release_reason VARCHAR(32)`. Included in `db.changelog-master.xml`. Rollback drops the column. |
| `ecm-core/src/main/resources/db/changelog/db.changelog-master.xml` | One-line include of `094-add-legal-hold-release-reason.xml` between `093-...` and `007-insert-initial-data.xml`. |
| `ecm-core/src/main/java/com/ecm/core/entity/LegalHold.java` | New `HoldReleaseReason` enum (`LITIGATION_ENDED`, `SCHEDULED_DISPOSITION`, `REQUEST_BY_REQUESTOR`, `OTHER`). New `releaseReason` field with `@Enumerated(EnumType.STRING)` + `@Column(name = "release_reason", length = 32)`. Nullable so legacy released rows remain legal. |
| `ecm-core/src/main/java/com/ecm/core/service/LegalHoldService.java` | Major refactor for orchestration pattern. Dropped class-level `@Transactional`; per-method annotations now explicit. New explicit constructor accepts `PlatformTransactionManager` and builds two `TransactionTemplate` fields (`parentHoldTransactionTemplate` REQUIRED + `bulkRowTransactionTemplate` REQUIRES_NEW). `createHold` annotated `@Transactional(propagation = NOT_SUPPORTED)` — outer method runs without transaction; parent hold saved in first template execute (commits), then per-row items applied in second template execute (each REQUIRES_NEW sees the already-committed parent through the FK), then read-only reload projects the DTO with `bulkApplyResults` attached. `releaseHold` requires non-null `releaseReason` (throws `IllegalArgumentException` → HTTP 400 otherwise). New private helper `applyBulkItems` + `applyOneItem` for the per-row loop. New typed exceptions `NodeNotFoundForHoldException` / `NodeNotVisibleForHoldException`. New records: `BulkApplyRequest` (extending `CreateLegalHoldRequest` with optional `nodeIds`), `BulkApplyResults` (wrapper holding `rows: List<BulkApplyResult>`), `BulkApplyResult` (status / item / errorCategory / errorMessage with static factories `added`/`skippedDuplicate`/`failed`), `BulkApplyErrorCategory` enum (`NODE_NOT_FOUND`, `NODE_NOT_VISIBLE`, `INTERNAL_ERROR`). `LegalHoldDto` and `LegalHoldSummaryDto` gain `releaseReason`; `LegalHoldDto` also gains `bulkApplyResults`. Per `feedback_sanitize_throwable_cause_for_log_emission`: INTERNAL_ERROR never echoes `ex.getMessage()` in response, never passes raw `Throwable` to SLF4J (only `ex.getClass().getSimpleName()` + fixed copy). Stable error-message constants `BULK_APPLY_ERROR_*` exported package-private for test assertion. |
| `ecm-core/src/main/java/com/ecm/core/controller/LegalHoldController.java` | **Unchanged.** Class-level `@PreAuthorize("hasRole('ADMIN')")` already covers all routes. Request shape additions (`nodeIds` on create, `releaseReason` on release) are backward-compatible with the existing `@RequestBody` mapping. |

### Frontend (TypeScript / React)

| File | Change |
|---|---|
| `ecm-frontend/src/services/legalHoldService.ts` | New types `HoldReleaseReason`, `BulkApplyErrorCategory`, `BulkApplyResultStatus`, `BulkApplyResult`, `BulkApplyResults`. `LegalHoldSummary` and `LegalHoldDetail` gain optional `releaseReason`. `LegalHoldDetail` also gains optional `bulkApplyResults`. `CreateLegalHoldRequest` gains optional `nodeIds: string[]`. `ReleaseHoldRequest` now requires `releaseReason: HoldReleaseReason` (TypeScript-level guard). New sentinels `LEGAL_HOLD_BULK_CREATE_UNEXPECTED_RESPONSE_MESSAGE` and `LEGAL_HOLD_RELEASE_UNEXPECTED_RESPONSE_MESSAGE` thrown by the dedicated predicates `assertLegalHoldDetailBulkCreate` / `assertLegalHoldDetailRelease`. Extended `isLegalHoldDetail` predicate to validate the new optional fields including `BulkApplyResults` wrapper shape, ADDED-without-item rejection, FAILED-without-errorCategory rejection, and unknown enum values rejection. |
| `ecm-frontend/src/pages/LegalHoldsPage.tsx` | Export new `parseUuidList(raw)` helper (newline/comma/semicolon split + trim + lowercase + 8-4-4-4-12 generic UUID validation + dedupe; **not** v4-only per gate correction). `CreateHoldDialog` refactored: adds optional Node-IDs textarea + parses via `parseUuidList`; submit forwards `nodeIds` (or `undefined` for back-compat) and processes returned `bulkApplyResults` (aggregate toast for ADDED/SKIPPED/FAILED counts; on partial failure, dialog stays open + drains successful UUIDs from textarea + renders dedicated Alert listing failed rows with per-category test IDs). `ReleaseHoldDialog` refactored: adds required `Select` for release reason (4 options); submit disabled until reason chosen; forwards `releaseReason` + `comment`. Detail panel header renders per-reason chip via `HOLD_RELEASE_REASON_CHIP_COLOR` map (success/info/warning/default), or `"Legacy release"` outlined chip when status is RELEASED but releaseReason is null. Page-level summary patch / handleCreated forward `releaseReason` to the summary row so the list also reflects the new state. |

### Tests

| File | Change |
|---|---|
| `ecm-core/src/test/java/com/ecm/core/service/LegalHoldServiceTest.java` | Constructor signature updated to inject `PlatformTransactionManager` mock with lenient stub `getTransaction → new SimpleTransactionStatus()`. Existing `releaseHoldMarksHoldReleased` updated to pass `releaseReason` per the new request shape. 9 new tests: `releaseHoldRequiresReleaseReason`, `getHoldLegacyReleasedRowTolerantToNullReason`, `createHoldWithoutNodeIdsLeavesBulkApplyResultsNull`, `createHoldOrchestratorParentBeforeRowItem` (Mockito `InOrder` lock — the strongest test that catches a future regression reintroducing outer `@Transactional` on createHold), `createHoldWithNodeIdsAllAdded`, `createHoldWithMissingNodePartialFailureDoesNotRollback`, `createHoldWithDuplicateSkipped`, `createHoldWithTenantInvisibleNodeReportsCategory`, `createHoldInternalErrorSanitisedMessage` (USER_PII probe + `RuntimeException` class name preservation), `createHoldBlankNameRejected`, `createHoldSecurityGate`. |
| `ecm-core/src/test/java/com/ecm/core/controller/LegalHoldControllerTest.java` | Existing DTO literals updated to include `releaseReason` + `bulkApplyResults` (additive fields, default `null`). 3 new tests: `createHoldWithNodeIdsReturnsBulkApplyResults` (verifies the wrapper shape in the response body), `releaseHoldMissingReasonReturns400`, `releaseHoldWithReasonReturns200WithBody`. |
| `ecm-core/src/test/java/com/ecm/core/controller/LegalHoldControllerSecurityTest.java` | **Unchanged.** Class-level `@PreAuthorize("hasRole('ADMIN')")` already covers the request-shape additions; no new endpoint, no new security case. |
| `ecm-frontend/src/services/legalHoldService.test.ts` | Existing `releaseHold` test updated to include `releaseReason`. Existing malformed-readback tests for `createHold` and `releaseHold` updated to use the new dedicated sentinels. 8 new tests covering `bulkApplyResults` wrapper parsing, `null` back-compat, predicate rejection of ADDED-without-item, predicate rejection of FAILED-without-errorCategory, unknown `errorCategory`, legacy released-hold with `releaseReason=null`, unknown `releaseReason` value. |
| `ecm-frontend/src/pages/LegalHoldsPage.test.tsx` (new) | 11 tests: 4 `parseUuidList` unit tests (split / case-insensitive dedupe / generic-UUID-not-v4-only / blank input); 3 create-dialog flow tests (all-success / partial-failure stays open + drains textarea + Alert / no-nodeIds back-compat); 2 release-dialog tests (submit disabled until reason / submit forwards `releaseReason` + comment); 2 release-reason chip rendering tests (known reason chip + legacy chip). |

## Verification (local)

| Check | Result |
|---|---|
| Frontend targeted Jest (service + page combined) | **37 / 37 pass** |
| `npm run lint` | **clean** |
| `CI=true npm run build` | **build succeeds** |
| `git diff --check -- . ':!.env'` | **clean** |
| `./ecm-core/mvnw -Dtest='LegalHoldServiceTest,LegalHoldControllerTest,LegalHoldControllerSecurityTest' test` | **blocked** by missing Docker socket on this dev box (same as past 13+ slices); CI is the authoritative gate. |

## Expected CI gate (7 / 7 green)

- Backend Verify — load-bearing on the migration `094` and the orchestration pattern. The Liquibase boot phase runs `094` in the integration profile, materializing the `release_reason` column; the entity's `@Enumerated(EnumType.STRING)` mapping then round-trips through repository tests. The 9 new service tests + 3 new controller tests cover the rest.
- Frontend Build & Test — covers the 11 new page tests + 8 new service tests.
- Phase C Security Verification — `requireAdmin()` is the gate; class-level `@PreAuthorize` preserved.
- Acceptance Smoke (3 admin pages)
- Property Encryption Closeout Gate
- Phase 5 Mocked Regression Gate — the new bulk-create and release sentinels are the load-bearing predicates against Phase-5-Mocked HTML fallback on the changed request shapes.
- Frontend E2E Core Gate

## Commit sequence

Per `docs(core) → fix(core) → docs(core) → docs(core) [skip ci]` pattern from past slices (e.g. `e7f76c3` + `5d374a3` + `2da7fd6` + `2e6406a`):

1. **`fix(core):`** — backend (migration + entity + service refactor) + frontend (service + page) + tests bundled together.
2. **`docs(core):`** — design brief + this verification doc.
3. (Conditional) **`test(core):`** — align fix if CI #1 surfaces a Mockito strict-stub issue, an integration test failure, or a schema validation drift. Pre-empted in the brief but real DB / proxy interactions can still surprise.
4. **`docs(core): ... [skip ci]`** — CI Follow-Up section with the green run id.

CI gate per `feedback_gh_run_watch_unreliable`: gate on `gh run view conclusion=success`, never on `gh run watch` exit code.

## OOS reaffirmation

- No change to `release_comment` semantics (still TEXT, nullable, optional).
- No backfill of existing released rows with default reason.
- No bulk release across N holds (release one hold at a time).
- No CSV / file import for node IDs.
- No async / background worker for bulk-apply (synchronous per-row).
- No email notification on release.
- No new HTTP routes — both halves are request-shape extensions to existing routes.
- No frontend service migration from `api.<verb><unknown>` shape (preserved canonical pattern).
- No `.env`, `application*.yml`, `docker-compose*`, `logback-spring.xml` touch.

## CI Follow-Up

Populated after CI completes.

- GitHub Actions run: `<pending>`
- Head: `<pending>`
- Result: `<pending>`
