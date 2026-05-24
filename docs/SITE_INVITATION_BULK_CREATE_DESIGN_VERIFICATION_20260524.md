# Site Invitation Bulk Create — Design & Verification

Date: 2026-05-24

## Context

Implementation of the bulk-create slice scoped in `docs/SITE_INVITATION_BULK_CREATE_DESIGN_20260524.md`. Two gate-applied corrections to the brief are reflected in the production code:

1. **Spring transaction self-invocation does not engage the proxy** — the per-row `REQUIRES_NEW` semantics required `TransactionTemplate.execute(...)` rather than a same-bean `@Transactional` private method call. Implemented via an explicit constructor that builds the template from an injected `PlatformTransactionManager`.
2. **`INTERNAL_ERROR` must not echo raw exception content** — `BulkInviteResult.errorMessage` for the catch-all branch carries a fixed copy + `ex.getClass().getSimpleName()` only. Server-side log emission uses `log.debug` with only the class name; no `Throwable` is passed to SLF4J (per `feedback_sanitize_throwable_cause_for_log_emission`).

## Production changes

### Backend

| File | Change |
|---|---|
| `ecm-core/src/main/java/com/ecm/core/service/SiteInvitationService.java` | Drop `@RequiredArgsConstructor`. New explicit constructor accepts `PlatformTransactionManager` and constructs a `TransactionTemplate` with `PROPAGATION_REQUIRES_NEW`. Add `BulkInviteRequest`/`BulkInviteResponse`/`BulkInviteResult` records + `BulkInviteErrorCategory` enum + two typed `IllegalArgumentException` subclasses (`BlankInviteeEmailException`, `DuplicatePendingInvitationException`) for clean catch-dispatch. Extract `createInvitationRow(Site, String, InviteRequest)` from the existing `invite(...)` body so both single-invite and bulk paths share it. New `inviteBulk(...)` method runs each row via `bulkRowTransactionTemplate.execute(...)`. Per-row catch handles INVALID_EMAIL / DUPLICATE_PENDING / EMAIL_SEND_FAILED (still SUCCESS) / INTERNAL_ERROR with stable fixed copies; INTERNAL_ERROR adds class simple name only. |
| `ecm-core/src/main/java/com/ecm/core/controller/SiteInvitationController.java` | Add `POST /api/v1/sites/{siteId}/invitations/bulk` returning `BulkInviteResponse`. HTTP 200 on any service-evaluated response (partial failures are body content, not HTTP errors); existing `IllegalArgumentException → 400` mapping covers the empty-array case. |

### Frontend

| File | Change |
|---|---|
| `ecm-frontend/src/services/siteInvitationService.ts` | Add `BulkInviteRequest`/`BulkInviteResponse`/`BulkInviteResult`/`BulkInviteErrorCategory` TS types. Add `SITE_INVITATION_BULK_CREATE_UNEXPECTED_RESPONSE_MESSAGE` sentinel (distinct from the resend sentinel for Phase 5 Mocked HTML-fallback diagnosis). Add `assertBulkInviteResponse` predicate + `isBulkInviteResult` helper. Add `createInvitationsBulk(siteId, request)` method. |
| `ecm-frontend/src/pages/SiteInvitationsPage.tsx` | Export new `parseBulkEmails(raw)` helper (newline/comma/semicolon split + trim + lowercase + first-seen dedup). Refactor `CreateInvitationDialog` to be bulk-only: replace single-email `TextField` with a `multiline` textarea + helper text + parsed-count display. Submit calls `createInvitationsBulk` exclusively (single-email case goes through the bulk endpoint too — single frontend code path). Add aggregate toast logic (sent / email-send-failed / row-failed / pending). On partial failure: stay open, drain successful emails from the textarea, show failed rows in a dedicated `Alert`. Replace `onCreated(invitation)` prop with `onBulkCreated(response)` and the page-level handler that prepends only SUCCESS rows to the table. |

## Tests added / updated

### Backend (Java, blocked locally by missing Docker socket — CI is the gate)

| File | What's locked |
|---|---|
| `SiteInvitationServiceTest.java` | Constructor signature update (+ `PlatformTransactionManager` mock with lenient `getTransaction → SimpleTransactionStatus`). 8 new bulk tests: all-SUCCESS, partial-failure-no-rollback (REQUIRES_NEW callout in test name), INVALID_EMAIL fixed copy, DUPLICATE_PENDING fixed copy + probe email `<script>alert(1)</script>@example.com` does NOT echo to `errorMessage`, EMAIL_SEND_FAILED remains SUCCESS, INTERNAL_ERROR includes class name but excludes the probe substring `USER_PII_FROM_EXCEPTION_LEAK_PROBE`, shared-role applied to every row, empty array → 400 at request boundary, security gate enforced (non-manager non-admin → `AccessDeniedException` + zero saves). |
| `SiteInvitationControllerTest.java` | 3 new bulk tests: all-SUCCESS body, partial-failure body returns HTTP 200 (not 400 — load-bearing), empty array maps `IllegalArgumentException` → 400. |
| `SiteInvitationControllerSecurityTest.java` | 4 new bulk security tests: unauth → 401, authenticated user reaches endpoint (manager/admin gate enforced inside service), `SecurityException` from service → 403, `IllegalArgumentException` (empty array) → 400. |

### Frontend (Jest, run locally — all green)

| File | What's locked |
|---|---|
| `siteInvitationService.test.ts` | 5 new bulk tests: SUCCESS+FAILED mixed response shape; HTML fallback throws bulk sentinel; missing `results` array throws sentinel; SUCCESS row without invitation payload throws sentinel (shape drift); FAILED row with invitation payload throws sentinel (shape drift); unknown `errorCategory` throws sentinel. |
| `SiteInvitationsPage.test.tsx` | 3 new `parseBulkEmails` unit tests (split / case-insensitive dedup / blanks). 5 new dialog-flow tests: all-SUCCESS closes dialog + toast + prepends rows; partial-failure stays open + drains textarea + Alert visible; sentinel error path toasts the sentinel literal; submit button disabled while textarea has no parseable emails; email-send-failed surfaces aggregate error toast and closes dialog (table shows the FAILED chip via the existing `SendStatusCell`). Plus the existing "email-send failure" test adapted from single-invite to bulk shape. |

## Verification

### Local

| Check | Status |
|---|---|
| `npm test -- --runTestsByPath src/services/siteInvitationService.test.ts src/pages/SiteInvitationsPage.test.tsx` | **39 / 39 pass** (Site invitation service + page suites combined) |
| `npm run lint` | **clean** |
| `CI=true npm run build` | **build succeeds** (one CI strict-cast adjustment for the predicate's `value as unknown as BulkInviteResponse` after the predicate proved shape) |
| `./mvnw -Dtest='SiteInvitationServiceTest,SiteInvitationControllerTest,SiteInvitationControllerSecurityTest' test` | **blocked by missing Docker socket** (same as past 12+ slices); CI is the authoritative gate |
| `git diff --check -- . ':!.env'` | **clean** |
| Working tree | `M .env` (pre-existing) + 5 production files + 4 test files + 3 docs (discovery + brief + this verification) |

### CI gate (must be 7 / 7 green)

- Backend Verify (gates the 15 new backend tests)
- Frontend Build & Test (gates the 13 new frontend tests + lint)
- Phase C Security Verification
- Acceptance Smoke (3 admin pages)
- Property Encryption Closeout Gate
- Phase 5 Mocked Regression Gate
- Frontend E2E Core Gate (the new bulk sentinel is the load-bearing predicate against Phase-5-Mocked HTML fallback on the new `/bulk` route)

## Commit sequence

1. **`fix(core):`** — backend + frontend production code + tests bundled (`feedback_per_slice_fix_commit_stages_code_and_test`: stage code AND test in one commit; zero in-scope `??` before commit).
2. **`docs(core):`** — verification doc (this file) + product discovery doc + design brief (gate brief allows bundling these three with the implementation slice's docs commit).
3. **`docs(core): ... [skip ci]`** — after CI conclusion is `success`, append `CI Follow-Up` to this doc with the run id and head SHA.

CI gate per `feedback_gh_run_watch_unreliable`: gate on `gh run view conclusion`, never on `gh run watch` exit code.

## Out of scope (preserved from the brief)

- No `SiteInvitation` schema column / new migration.
- No CSV / file upload UI.
- No async / background send worker.
- No rate limiter / bulk-size cap.
- No email template change.
- No per-row role / per-row message override.
- No streaming progress events.
- No removal of the existing single-invite endpoint (kept for external callers).
- No frontend service migration from `api.<verb><Typed>` to `api.<verb><unknown>` (the existing "D*" stylistic deviation preserved).
- No `.env` / `application*.yml` / `docker-compose*` / Liquibase changelog touch.

## CI Follow-Up

Final CI:

- GitHub Actions run: `26358500029`
- Head: `2da7fd6` (align-fix commit; the production+test commit was `e7f76c3` and was followed by docs `5d374a3`, then `2da7fd6` adjusting the empty-array test for Mockito strict-stub mode — see §"Align-fix narrative" below)
- Result: **success**

All seven jobs passed:

- Backend Verify
- Frontend Build & Test
- Phase C Security Verification
- Acceptance Smoke (3 admin pages)
- Property Encryption Closeout Gate
- Phase 5 Mocked Regression Gate
- Frontend E2E Core Gate

## Align-fix narrative

The first CI run `26358365652` on `5d374a3` (head after the docs commit which itself sits on `e7f76c3`) failed Backend Verify with a single error: `SiteInvitationServiceTest.inviteBulkEmptyArrayThrows » UnnecessaryStubbing`.

Root cause: the test called the shared `setUpBulkBaseSite(...)` helper, which stubs the site repository, security service, and site-member repository. But the empty-array guard inside `inviteBulk(...)` fires BEFORE site load and security check, so none of those stubs were exercised. Mockito strict mode (default under `@ExtendWith(MockitoExtension.class)`) rejected the unused stubs.

Fix `2da7fd6`: drop `setUpBulkBaseSite` from the empty-array test. The test now constructs the service with the bare `newService()` (which only stubs the `transactionManager` leniently) and adds a `verify(invitationRepository, never()).save(...)` assertion to lock the "empty array short-circuits before any save attempt" behavior.

Per-CI-round single-root-cause discipline (`feedback_diagnostic_cadence_for_opaque_500s`): the fix touches one test file only. No production code change.

The other test-output noise visible in the failed run's log (`Caused by: java.lang.RuntimeException: scan failed: boom` from `RmReportPresetController` test setup) is intentional test diagnostic output, not a test failure — the failed-test summary at the bottom of the log shows `[ERROR] Tests run: 2200, Failures: 0, Errors: 1, Skipped: 6` with the single error being the SiteInvitation one. Confirmed isolation to the unused-stub case.

## Track status

Bulk site invitation create slice is **shipped**. Frontend dialog now defaults to the bulk endpoint for any N >= 1 emails; the backend single endpoint remains for external callers; per-row send-tracking is preserved verbatim by reusing the existing `sendInvitationEmail` path inside `createInvitationRow`.

Follow-up candidates surfaced during this slice but explicitly OOS (see brief §"Out of scope"): CSV upload, async send worker, rate limiter / bulk-size cap, per-row role / message override, streaming progress events. These can be opened as separate slices only if operator signals call for them.
