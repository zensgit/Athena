# Site Invitation Bulk Create — Implementation Brief

Date: 2026-05-24

## Context

The site invitation surface shipped its single-email send-tracking + resend layer on 2026-05-07 (`docs/SITE_INVITATION_LIVE_SEND_CLOSEOUT_20260507.md`) and shipped frontend-only bulk-resend of FAILED rows on 2026-05-11 (`docs/SITE_INVITATION_BULK_FAILED_RESEND_DESIGN_VERIFICATION_20260511.md`). The remaining operator-visible gap surfaced by `docs/PRODUCT_CAPABILITY_DISCOVERY_20260524.md` Top 3 #1 is **bulk-create**: operators currently must open the invite dialog once per recipient.

This brief specifies the implementation. Scope is intentionally narrow: paste-and-create with per-row send-tracking retained on every row.

## Scope

### In scope

- Backend `POST /api/v1/sites/{siteId}/invitations/bulk` endpoint that accepts an array of `InviteRequest`-shaped items and returns a per-row result list (success rows include the full `SiteInvitationDto`; failed rows include the email + error category + error message).
- Frontend `CreateInvitationDialog` updated to accept a textarea with newline / comma / semicolon-separated emails + shared role + shared optional message; client-side parse / trim / dedupe; aggregate result toast.
- Frontend service: new `createInvitationsBulk(siteId, request)` method with response-shape guard mirroring the existing `assertSiteInvitationDto` + `assertSiteInvitationArray` pattern.
- Backend per-row try/catch with partial-success semantics (one failure does NOT roll back the others).
- Targeted unit + integration tests on both backend and frontend.

### Out of scope (explicit)

- CSV / file upload — only paste-into-textarea is supported.
- Background worker / async send — per-row email send is synchronous in the same request, matching the existing single-invite semantics.
- Rate limiter on bulk size — the existing single-invite has no rate limiter; bulk inherits the same operator-trust model.
- Email template changes (`site.invitation` template stays identical).
- `SiteInvitation` schema changes (no new columns, no new migration). The per-row send-tracking columns already exist from migration `092-add-site-invitation-send-tracking.xml`.
- Per-row role / per-row message override — role and message are shared across all rows in the same bulk request. Per-row customisation can be a future slice.
- Visual progress bar during bulk send (operator sees a spinner + final toast; per-email progress streaming is OOS).
- New email send mechanism — reuses the existing `EmailNotificationService` synchronous path used by single-invite.
- Removing the single-invite endpoint — kept for API compatibility / external callers.
- `.env`, `application*.yml`, `docker-compose*`, Liquibase changelog.

## Required reading before code work begins

- `ecm-core/src/main/java/com/ecm/core/controller/SiteInvitationController.java` — endpoint patterns + `@PreAuthorize("isAuthenticated()")` (manager gate inside service).
- `ecm-core/src/main/java/com/ecm/core/service/SiteInvitationService.java` — `invite(...)` (`:55-94`), `sendInvitationEmail(...)` (`:326-375`), `ensureCanManageInvitations(...)` (`:267-277`), `normalizeEmail(...)` (`:285-291`), `normalizeRole(...)` (`:293-304`), `InviteRequest`/`SiteInvitationDto` records (`:418, :420-441`).
- `ecm-core/src/test/java/com/ecm/core/service/SiteInvitationServiceTest.java` — existing test patterns for invite + send-tracking outcomes.
- `ecm-core/src/test/java/com/ecm/core/controller/SiteInvitationControllerTest.java` — controller-level test pattern with mocked service.
- `ecm-core/src/test/java/com/ecm/core/controller/SiteInvitationControllerSecurityTest.java` — `@WebMvcTest` security pattern (per `CLAUDE.md` §"Controller Security Tests" — any new controller endpoint must add a security test row).
- `ecm-frontend/src/services/siteInvitationService.ts` — `assertSiteInvitationDto` / `assertSiteInvitationArray` + `SITE_INVITATION_RESEND_UNEXPECTED_RESPONSE_MESSAGE` sentinel. Note this service is "D*" tier (typed-generic + assert) per the frontend guard inventory — bulk additions can match this style or switch to `api.<verb><unknown>` + assert (recommended: keep stylistic consistency, use typed generic).
- `ecm-frontend/src/services/siteInvitationService.test.ts` — service-shape test pattern.
- `ecm-frontend/src/pages/SiteInvitationsPage.tsx` — current `CreateInvitationDialog` (`:139-247`), `buildSendFailureMessage` helper (`:82-89`), `handleSubmit` toast logic (`:166-191`).
- `ecm-frontend/src/pages/SiteInvitationsPage.test.tsx` — page-level test pattern, including the bulk-resend tests added on 2026-05-11.
- `docs/SITE_INVITATION_BULK_FAILED_RESEND_DESIGN_VERIFICATION_20260511.md` — prior precedent for bulk-action UX (eligibility table, aggregate-toast rules, per-row DTO merge on response).

## Backend design

### Endpoint

- New: `POST /api/v1/sites/{siteId}/invitations/bulk`.
- Existing `POST /api/v1/sites/{siteId}/invitations` (single) **kept unchanged** for backward compatibility and external callers. The frontend `CreateInvitationDialog` will only call the bulk endpoint from this slice forward (even for "exactly 1 email" — single code path).
- HTTP status: `200 OK` for any response that the service actually evaluated. `400 Bad Request` only on request-shape errors (`siteId` missing, body not parseable, `inviteeEmails` empty array). **Per-row failures inside the bulk DO NOT change the response status** — partial success returns 200 with a mix of per-row outcomes.

### Request DTO

Recommend `inviteeEmails: List<String>` (not a single `rawText`). Reasoning:

- Backend stays simple — no parser dependency, no decision about whether to accept commas vs semicolons.
- Frontend already needs the parse / dedupe / trim logic for the textarea; centralising it in the frontend means the parser is testable with frontend's existing Jest test infrastructure.
- A future CSV-upload feature (OOS for this slice) can also produce a `List<String>` without protocol change.

```java
public record BulkInviteRequest(
    List<String> inviteeEmails,
    String invitedRole,    // shared across all rows; same nullable / default-CONSUMER semantics as single
    String message         // shared across all rows; optional
) {}
```

Validation:

- `inviteeEmails == null || inviteeEmails.isEmpty()` → 400 with deterministic message `"inviteeEmails must contain at least one entry"`.
- Per-row blank / null normalisation — reuse `normalizeEmail(...)` (existing private helper, will need to be made package-private OR a new public-facing normaliser added; recommend keeping `normalizeEmail` private and doing the normalisation inside the new bulk service method).
- Recommend no upper bound on `inviteeEmails.size()` in v1 — the existing single-invite path has no rate limiter and operators are trusted. If a future incident surfaces abuse, a property-driven cap can be added without protocol change.

### Response DTO

Per-row result list. Successful rows carry the full `SiteInvitationDto` (so the frontend can merge into its existing table with no shape divergence). Failed rows carry the input email + a structured error category + a free-text message.

```java
public record BulkInviteResponse(
    List<BulkInviteResult> results
) {}

public record BulkInviteResult(
    String inviteeEmail,                  // normalised form when normalisation succeeded; raw form when normalisation failed
    Status status,                        // SUCCESS or FAILED
    SiteInvitationDto invitation,         // non-null only when status == SUCCESS
    String errorCategory,                 // non-null only when status == FAILED; enum-shaped values described below
    String errorMessage                   // non-null only when status == FAILED; human-readable detail
) {
    public enum Status { SUCCESS, FAILED }
}
```

Error categories (suggested enum-shaped values; the frontend treats them as opaque strings but they need to be stable):

- `INVALID_EMAIL` — `normalizeEmail` returned null (blank / empty input). `errorMessage` is a stable fixed copy, e.g. `"Email address is blank or invalid."`. Original input email is echoed in `inviteeEmail` (the raw form, since normalisation failed). No exception object is captured.
- `DUPLICATE_PENDING` — typed exception from the existing duplicate-pending guard. `errorMessage` is a stable fixed copy referencing the operator-actionable hint, e.g. `"A pending invitation already exists for this email in this site."`. The original `IllegalArgumentException.getMessage()` (which embeds the email + siteId via string concatenation) is **not** copied verbatim into `errorMessage` to avoid coupling the API response to a debug-shaped exception string.
- `EMAIL_SEND_FAILED` — invitation was created successfully but `sendInvitationEmail` recorded `lastSendStatus = FAILED`. **This is NOT a per-row failure** in the `Status.FAILED` sense — the invitation row exists, the operator can resend. Recommend: this case is `Status.SUCCESS` with the returned DTO carrying `lastSendStatus = FAILED`, mirroring single-invite semantics where `invite(...)` returns the DTO even on email failure. The frontend toast logic already handles this case correctly.
- `INTERNAL_ERROR` — any other `Throwable` caught by the per-row try/catch. **Gate-corrected 2026-05-24:** `errorMessage` must NOT contain `ex.getMessage()`. Use a stable fixed copy plus the exception's class simple name, e.g. `"Unexpected error during invitation creation (RuntimeException)."`. Concrete `getMessage()` / stack are logged server-side at DEBUG level via `log.debug(...)` with **only** `ex.getClass().getSimpleName()` interpolated into the message — never `log.error("...", ex)` with the raw `Throwable` (per `feedback_sanitize_throwable_cause_for_log_emission` memory: SLF4J's `(msg, ex)` overload would walk the cause chain and emit any provider body / user input embedded in the chain). If deeper diagnostics are required, the operator inspects the server log at DEBUG level on the specific row's timestamp.

### Service method

```java
// Outer method itself is NOT @Transactional. Site load + security check
// happen outside any transaction; per-row work runs inside a TransactionTemplate
// callback so each row commits independently. See "Transactional strategy" below
// for why self-call @Transactional was rejected.
public BulkInviteResponse inviteBulk(String siteId, BulkInviteRequest request) {
    // 1) Load site + ensureCanManageInvitations(...) — single security check at request boundary
    // 2) For each inviteeEmail:
    //    a) transactionTemplate.execute(status -> createInvitationRow(site, currentUser, perRowRequest))
    //    b) Catch IllegalArgumentException from createInvitationRow -> map to INVALID_EMAIL or DUPLICATE_PENDING
    //       (use typed exceptions inside the helper so the bulk catch can dispatch without message inspection)
    //    c) Catch any other Throwable -> INTERNAL_ERROR; errorMessage = stable fixed copy + ex.getClass().getSimpleName()
    //                                    NEVER include ex.getMessage() in the response or in SLF4J args
    // 3) Return BulkInviteResponse with per-row results
}
```

**Transactional strategy (gate-corrected 2026-05-24):**

- **Outer method is NOT `@Transactional`.** Site load + security check do their own short-lived read in the request thread; no outer transaction is needed and adding one would inherit `@Transactional`'s default REQUIRED propagation that interacts badly with per-row REQUIRES_NEW.
- **Per-row work uses `TransactionTemplate.execute(...)`** to start a new transaction per row. **Spring transaction proxies do not intercept self-calls inside the same bean**, so the previously-drafted approach of "annotate a private/same-bean method with `@Transactional(propagation = REQUIRES_NEW)` and call it from `inviteBulk`" would silently fall back to no-transaction-at-all and break per-row isolation. The two correct options are:
  - **Recommended:** inject `TransactionTemplate transactionTemplate` (constructed in a `@Configuration` bean or via `new TransactionTemplate(platformTransactionManager)` if a bean already exists) and call `transactionTemplate.execute(status -> createInvitationRow(...))` in the per-row loop. Smaller code change; same bean.
  - Alternative: extract a sibling `SiteInvitationBulkRowService` bean whose `@Transactional(propagation = REQUIRES_NEW)` method is invoked via Spring proxy. Bigger refactor; only worth doing if `TransactionTemplate` is otherwise blocked.
- The new `createInvitationRow(Site, currentUser, InviteRequest)` helper IS extracted from the existing `invite(...)` body (the post-security-check part starting around `SiteInvitationService.java:60-93`). Both `invite(...)` and the bulk path call it. The bulk path skips re-loading the site and re-running the security check per row.
- The `sendInvitationEmail(...)` call inside `createInvitationRow` already self-persists send-tracking columns via its own `invitationRepository.save(invitation)` (`SiteInvitationService.java:374`). That self-save runs inside the row's `TransactionTemplate` transaction and commits on row success.

This correction is recorded here verbatim so the executor does not silently revert to the original incorrect design under code-review pressure.

### Security

- Same `ensureCanManageInvitations(site, currentUser)` check as single-invite, run **once** at the bulk method entry. Not re-run per row (the site is the same, the user is the same).
- Controller-level `@PreAuthorize("isAuthenticated()")` matching the single endpoint; per-site authorisation enforced by `ensureCanManageInvitations` (admin or site manager).
- **Mandatory new `*SecurityTest.java` row** per `CLAUDE.md` §"Controller Security Tests": extend `SiteInvitationControllerSecurityTest.java` with the bulk endpoint's authenticated / unauthenticated / non-manager / manager / admin cases. Do not create a parallel test class.

### Email send semantics

- Each row inside the bulk loop calls `sendInvitationEmail(invitation, site)` synchronously, identical to single-invite. SMTP latency × N adds up — for an operator pasting 20 emails, this could be ~20-60 seconds wall time. Acceptable for v1; if profiling shows it is intolerable, an async-send slice can pick it up later (would need to address how `lastSendStatus = FAILED` rows surface to the operator without blocking the response).
- The bulk endpoint blocks on the request thread until all rows complete. No streaming, no progress events.

### Logging

Per the `feedback_sanitize_throwable_cause_for_log_emission` memory, any caught generic `Exception` in the per-row catch must NOT pass the raw exception object to SLF4J (`log.error(msg, ex)`); use `ex.getClass().getSimpleName()` and a sanitized message, mirroring the `MailAutomationController:362` callback pattern fixed in `41bd641`.

## Frontend design

### Dialog refactor

`CreateInvitationDialog` (`SiteInvitationsPage.tsx:139-247`) becomes:

- Single textarea labelled "Invitee emails", placeholder `"alice@example.com\nbob@example.com\nclaire@example.com"`, multi-line (recommended `rows={6}`), monospaced font helps operator review.
- Helper text below textarea: `"Newline-, comma-, or semicolon-separated. Duplicates and blanks are ignored."`
- Shared `Role` `Select` (unchanged from current).
- Shared `Message (optional)` `TextField` (unchanged from current).
- "Send invitations" submit button replaces "Send invitation" (singular → plural).

### Parse logic (frontend-owned)

Implement a pure helper, ideally exported from the dialog module for unit testing:

```typescript
function parseBulkEmails(raw: string): string[] {
  return raw
    .split(/[\n,;]/)          // newline OR comma OR semicolon
    .map(s => s.trim())
    .filter(s => s.length > 0)
    .map(s => s.toLowerCase())
    .filter((email, index, arr) => arr.indexOf(email) === index);  // first-seen dedupe
}
```

Empty result after parse → disable submit, show inline helper text `"Enter at least one email address."`. Match the existing `if (!email) return;` semantics at `SiteInvitationsPage.tsx:168`.

### Service method

Add to `ecm-frontend/src/services/siteInvitationService.ts`:

```typescript
export interface BulkInviteRequest {
  inviteeEmails: string[];
  invitedRole?: SiteMemberRole;
  message?: string;
}

export interface BulkInviteResult {
  inviteeEmail: string;
  status: 'SUCCESS' | 'FAILED';
  invitation: SiteInvitationDto | null;
  errorCategory: string | null;     // see backend enum-shaped categories
  errorMessage: string | null;
}

export interface BulkInviteResponse {
  results: BulkInviteResult[];
}

// Service method
async createInvitationsBulk(siteId: string, request: BulkInviteRequest): Promise<BulkInviteResponse> {
  const result = await api.post<BulkInviteResponse>(`/sites/${siteId}/invitations/bulk`, request);
  return assertBulkInviteResponse(result);
}
```

Predicate + sentinel:

- Add `SITE_INVITATION_BULK_CREATE_UNEXPECTED_RESPONSE_MESSAGE` constant (separate from the resend sentinel — different action surface, different error copy).
- Add `assertBulkInviteResponse(value: unknown): BulkInviteResponse` predicate. Structurally validates: top-level object, `results` is array, each entry is `BulkInviteResult`-shaped — `status` is `'SUCCESS' | 'FAILED'`, `invitation` is `SiteInvitationDto` or `null`, `errorCategory` / `errorMessage` nullable strings, `inviteeEmail` non-null string.
- The predicate is the regression-lock against Phase 5 Mocked HTML fallback (`feedback_phase5_mocked_html_fallback` memory). On mocked failure, throws the sentinel; consumer's existing `try/catch` in the dialog surfaces it as a toast.

### Submit + response handling

```typescript
const handleSubmit = async () => {
  const emails = parseBulkEmails(form.textarea);
  if (emails.length === 0) return;
  setSubmitting(true);
  try {
    const response = await siteInvitationService.createInvitationsBulk(siteId, {
      inviteeEmails: emails,
      invitedRole: form.invitedRole || 'CONSUMER',
      message: form.message?.trim() || undefined,
    });
    handleBulkResponse(response);
    onCreated(response);  // see merge logic below
    handleClose();
  } catch (err) {
    console.error('Failed to send bulk invitations', err);
    toast.error('Failed to send invitations.');
  } finally {
    setSubmitting(false);
  }
};
```

### Toast aggregation rules

Mirror the bulk-failed-resend precedent (`docs/SITE_INVITATION_BULK_FAILED_RESEND_DESIGN_VERIFICATION_20260511.md` §"The UX reports one aggregate toast"):

| Result mix | Toast severity | Copy template |
|---|---|---|
| all rows `SUCCESS` AND every returned `invitation.lastSendStatus === 'SENT'` | success | `"N invitation(s) sent."` |
| all rows `SUCCESS` AND some `invitation.lastSendStatus === 'FAILED'` | error | `"N invitation(s) created, M email send(s) failed. See the table for details."` |
| any row `Status.FAILED` | error | `"N invitation(s) created, M failed. See the dialog for details."` |
| mixed: some `SUCCESS` + some `FAILED` + some email-send-failed | error | combined message |

`HTTP success is not semantic success` per the `feedback_http_success_is_not_semantic_success` memory: never treat 200 + `Status.SUCCESS` as send success without inspecting the returned DTO's `lastSendStatus`.

### Failed-row display in dialog

When the response contains any `Status.FAILED` rows, do **not** auto-close the dialog. Instead:

- Show a `Material UI` `Alert severity="error"` block above the textarea listing the failed rows: `email + errorCategory + errorMessage`.
- Keep the textarea populated with **only the failed emails** so the operator can fix and re-submit (this is a deliberate UX choice — drains successful rows to avoid duplicate sends; failed rows remain editable in place).
- The list refresh still picks up the SUCCESS rows immediately, so the table reflects partial progress.

### Table merge

`onCreated` signature changes from `(invitation: SiteInvitationDto) => void` to `(response: BulkInviteResponse) => void`. The page-level merge:

- For each `result` where `status === 'SUCCESS'` and `invitation` is non-null, prepend the invitation to the existing list (matches single-invite's current behavior of "new invitations appear at top by createdDate desc").
- Failed rows are NOT added to the table (the dialog shows them; they are not persisted server-side).

## Tests

### Backend (Java)

| File | New tests |
|---|---|
| `SiteInvitationServiceTest.java` | `inviteBulkAllSuccess`, `inviteBulkPartialFailureDoesNotRollback`, `inviteBulkDuplicatePendingMarkedFailed`, `inviteBulkInvalidEmailMarkedFailed`, `inviteBulkEmailSendFailureStillSuccess`, `inviteBulkSharedRoleAppliedToAllRows`, `inviteBulkEmptyArrayThrowsIllegalArgument`, `inviteBulkSecurityGateEnforced` |
| `SiteInvitationControllerTest.java` | `bulkInviteEndpointReturns200OnSuccess`, `bulkInviteEndpointReturns400OnEmptyArray`, `bulkInviteEndpointPassesThroughPerRowResults`, `bulkInviteEndpointStatusIs200OnPartialFailure` |
| `SiteInvitationControllerSecurityTest.java` | `bulkInviteUnauthenticated401`, `bulkInviteRoleUser403`, `bulkInviteRoleSiteManager200`, `bulkInviteRoleAdmin200`, `bulkInviteNonMember403` — mirror the patterns from the single-invite tests in the same file. **Required per `CLAUDE.md` controller-security-test policy.** |

Key behavioral locks (gate-corrected):

- Partial-failure does NOT roll back successful rows (verify by injecting a mock that fails for row index 1 and asserting rows 0 and 2 are persisted). The test name should explicitly call out the `TransactionTemplate` per-row commit boundary so a future contributor cannot silently revert the design to a same-bean `@Transactional` self-call.
- `sendInvitationEmail` is called for every row that passed validation, regardless of whether prior rows succeeded.
- Per-row `BulkInviteResult` carries the returned `SiteInvitationDto` for success rows, with `lastSendStatus` reflecting the actual send outcome.
- **`INTERNAL_ERROR` row sanitization** — inject a `RuntimeException("USER_PII_FROM_EXCEPTION_LEAK_PROBE")` for one row and assert:
  - The returned `BulkInviteResult.errorMessage` does NOT contain `"USER_PII_FROM_EXCEPTION_LEAK_PROBE"`.
  - The captured log emission for that row (via `ListAppender` per the `OAuthCredentialServiceTest` precedent — also assert `Throwable.printStackTrace(PrintWriter)` serialization excludes the probe substring) does NOT contain `"USER_PII_FROM_EXCEPTION_LEAK_PROBE"`.
  - The exception's class simple name (e.g. `"RuntimeException"`) IS present in the log and IS present in `errorMessage` — confirming the corrections preserve enough operator-actionable signal without leaking raw content.
- **`DUPLICATE_PENDING` row sanitization** — the existing duplicate-pending `IllegalArgumentException` carries `"A pending invitation already exists for {email} in site {siteId}"`. Assert the bulk result's `errorMessage` is the fixed copy (not that string), so a malicious or accidentally-funny email like `<script>alert(1)</script>@example.com` doesn't get echoed back through the bulk response into the frontend dialog.

### Frontend (Jest)

| File | New tests |
|---|---|
| `siteInvitationService.test.ts` | `createInvitationsBulk parses backend response`, `createInvitationsBulk throws sentinel on HTML fallback`, `createInvitationsBulk throws sentinel on missing results field`, `assertBulkInviteResponse rejects non-array results`, `assertBulkInviteResponse accepts mixed success+failed rows` |
| `SiteInvitationsPage.test.tsx` | `parseBulkEmails splits newline / comma / semicolon`, `parseBulkEmails dedupes case-insensitively`, `bulk create dialog displays failed rows`, `bulk create dialog drains successful rows on partial failure and keeps failed in textarea`, `bulk create dialog merges successful invitations to table`, `bulk create dialog toast aggregates send statuses`, `bulk create dialog handles HTML fallback gracefully` |
| (existing single-invite tests) | Should continue to pass — the single-invite dialog code path is being replaced by bulk; if any test asserts against the single-invite-only call, update it to assert against bulk with `inviteeEmails: [oneEmail]`. |

## Verification

### Local

```bash
# Backend targeted
./ecm-core/mvnw -Dtest='SiteInvitationServiceTest,SiteInvitationControllerTest,SiteInvitationControllerSecurityTest' test
# Expected: blocked by missing Docker socket on this dev box per CLAUDE.md.
# CI is the authoritative execution gate (same pattern as past 12+ slices).

# Frontend targeted
cd ecm-frontend
CI=true npm test -- --runTestsByPath \
  src/services/siteInvitationService.test.ts \
  src/pages/SiteInvitationsPage.test.tsx \
  --watchAll=false
npm run lint
CI=true npm run build

# Whitespace
git diff --check -- . ':!.env'
```

### CI gate (must be 7/7 green)

- Backend Verify
- Frontend Build & Test
- Phase C Security Verification
- Acceptance Smoke (3 admin pages)
- Property Encryption Closeout Gate
- Phase 5 Mocked Regression Gate
- Frontend E2E Core Gate

The Phase 5 Mocked gate is particularly load-bearing for this slice because the bulk endpoint is new and Phase 5 mocks will hit the HTML-fallback path for the unmocked route. **The service-shape guard predicate must throw the new sentinel on the HTML response**; without it, Phase 5 will fail on a `JSON.parse` crash or on a missing `results` field.

## Commit sequence

Per past slice pattern (e.g., `f80bd8d` + `f604b1b` + `52db71c`):

1. `fix(core):` — production backend (controller + service + DTOs) + production frontend (dialog + service + predicate + sentinel) + new tests bundled together (`feedback_per_slice_fix_commit_stages_code_and_test` discipline: stage code AND test in one commit).
2. `docs(core):` — verification doc recording local verification outcomes + expected CI gate.
3. `docs(core): ... [skip ci]` — CI Follow-Up section populated after the CI run completes successfully.

If an align fix is needed after CI 1 (e.g., a Mockito strict-stub or response-shape edge case), insert a `test(core): align ...` commit between #1 and #2 mirroring `f604b1b`. **Gate on `gh run view conclusion=success`, never on `gh run watch` exit code** per `feedback_gh_run_watch_unreliable`.

## OOS reaffirmation

- No new `SiteInvitation` schema column / new migration.
- No CSV / file upload UI.
- No async / background send worker — synchronous SMTP latency × N per request.
- No rate limiter / bulk-size cap.
- No email template change (`site.invitation` stays as-is).
- No per-row role / per-row message override.
- No streaming progress events.
- No removal of the existing single-invite endpoint.
- No frontend service migration from `api.<verb><Typed>` to `api.<verb><unknown>` — the `siteInvitationService` keeps its "D*" stylistic deviation; bulk additions follow the same shape for consistency.
- No `.env` / `application*.yml` / `docker-compose*` / Liquibase changelog touch.

## Decision points the brief recommends

Listed here so a future reviewer can challenge each:

| Decision | Recommendation | Why |
|---|---|---|
| Request shape: `inviteeEmails: List<String>` vs `rawText` | **List<String>** | Backend stays parser-free; frontend already has Jest infra for parse tests; future CSV upload reuses the same protocol. |
| Endpoint: new `/bulk` vs overload single | **New `/bulk`** | Single endpoint stays back-compat for external callers; bulk response DTO can differ cleanly; controller test surface is clearer. |
| Frontend single-email path: keep single endpoint call vs always use bulk | **Always use bulk** (frontend dialog) | Single frontend code path, single test surface, single error / toast logic. Backend single endpoint stays for external callers. |
| Per-row transaction | **`TransactionTemplate.execute(...)`** per row; outer method is NOT `@Transactional` | Partial-success requires independent commit per row. Spring transaction proxies do not intercept same-bean self-calls, so `@Transactional(REQUIRES_NEW)` on a sibling private method called from `inviteBulk` would silently no-op. `TransactionTemplate` injection sidesteps the proxy issue with the smallest code footprint. |
| `EMAIL_SEND_FAILED` classification | **`Status.SUCCESS` with `lastSendStatus = FAILED`** | Mirrors single-invite where `invite(...)` returns the DTO even on send failure. Frontend toast logic already handles this case. |
| Bulk size cap | **No cap in v1** | Existing single-invite has no rate limit; trusted-operator model. Adds property-driven cap later if abuse surfaces. |
| Per-row role/message | **Shared only** | Keeps the dialog simple; per-row customisation is a future slice if requested. |

## Worker brief handoff

This document is the implementation brief. Executor (next agent or human) should:

1. Read all 9 sources listed in §"Required reading".
2. Implement backend per §"Backend design".
3. Implement frontend per §"Frontend design".
4. Add tests per §"Tests".
5. Run local verification, push, monitor CI, record green run.
6. Author the verification doc as a sibling of this brief: `docs/SITE_INVITATION_BULK_CREATE_DESIGN_VERIFICATION_20260524.md` (or `_<actual completion date>` if it slips).
7. Update `docs/SITE_INVITATION_RESEND_OPERATOR_RUNBOOK_20260507.md` with a §"Bulk create" subsection describing the new dialog and pointing to this design doc.

## What this brief does not commit to

- No code, no test, no doc beyond this brief itself has been written.
- No commits made.
- The executor still needs explicit "go" from the gate to begin the implementation slice; this brief alone does not authorize merge.
