# Sensitive-Data Logging Audit — Phase 2 Minimum Remediation Slice

Date: 2026-05-23

## Context

This slice executes the minimum Phase 2 remediation proposed in `docs/SENSITIVE_DATA_LOGGING_AUDIT_FINDINGS_20260523.md` §"Minimum Phase 2 remediation slice". The gate explicitly chose to open Phase 2 (not to close the track and not to expand to a broader Logback redactor) after Phase 1.5 positive-proof confirmed 6 STILL NEEDS-MASK sites, including the highest-risk site `MailFetcherService.safeSubject(...)` whose name is misleading (no redaction) and whose return value is interpolated at `MailFetcherService.java:786` into an ERROR-level log call.

Phase 2 scope is deliberately small. Only the four sites with concrete, evidence-driven risk are modified. Broader cleanup (the ~18 DEBUG-level mail-parsing exception logs and the generic-`Exception` ERROR/WARN sites at `:170`, `:384`) is intentionally deferred — it would amount to speculative cleanup without a same-slice unit test pinning the new behavior.

## Scope (production code changes)

| # | File:line | Change |
|---|---|---|
| 1 | `ecm-core/src/main/java/com/ecm/core/integration/mail/service/MailFetcherService.java` | Rename `private String safeSubject(Message)` → `String subjectOrEmpty(Message)` (package-private); the helper still returns the raw subject for non-logger consumers. Add new helper `String redactSubjectForLog(Message)` that always returns the literal `"<redacted-subject>"`. |
| 2 | `MailFetcherService.java:800` (was `:786`) | Replace `subjectOrEmpty(message)` with `redactSubjectForLog(message)` in the single ERROR-level log call. The 7 non-logger call sites (`:504, :529, :846, :2052, :2339, :2393, :2765`) continue to use `subjectOrEmpty` because they feed persistence, DTO construction, attachment filename, or message-ID fallback — not loggers. |
| 3 | `MailFetcherService.java:168-173` (was `:164`) | Replace `log.warn("OAuth reauth required for mail account {}: {}", account.getName(), e.getMessage())` with `log.warn("OAuth reauth required for mail account {} ({}): code={}", account.getName(), account.getId(), e.getOauthError())`. `e.getMessage()` embedded the provider-controlled `oauthErrorDescription`; `e.getOauthError()` returns only the standard OAuth error code (e.g. `invalid_grant`). |
| 4 | `ecm-core/src/main/java/com/ecm/core/integration/oauth/OAuthCredentialService.java:208-216` | Strip the `parsed.errorDescription()` append from the revoke-failure `IllegalStateException` message. The description is provider-controlled and was flowing through `RestExceptionHandler.handleInternalState` (`:44`) at ERROR + URI + full stack. After the change, only `parsed.error()` is in the message; the description remains accessible via the cause-chain (`ex`). |
| 5 | `OAuthCredentialService.java:354-358` | Same change for the refresh-failure path. |
| 6 | `ecm-core/src/main/java/com/ecm/core/integration/mail/controller/MailAutomationController.java:362` | Replace `log.warn("OAuth callback failed", ex)` (which passes the full `Throwable` to SLF4J) with `log.warn("OAuth callback failed: type={} (message redacted)", ex.getClass().getSimpleName())`. The OAuth callback exchanges an authorization code; any nested transport exception that echoes the request body could leak the code or provider error context. After the change, only the exception class simple name reaches the log sink. |

## Tests added

| # | File | What it locks |
|---|---|---|
| T1 | `ecm-core/src/test/java/com/ecm/core/integration/mail/service/MailFetcherServiceLoggingRedactionTest.java` (new) | 5 tests: `subjectOrEmpty` preserves raw subject; `subjectOrEmpty` handles null; `redactSubjectForLog` returns constant `<redacted-subject>` regardless of input; simulated `:800` log emission via the `MailFetcherService` logger emits the redaction marker and never the raw subject; simulated `:168` OAuth-reauth log emission emits OAuth error code + account identity, never the provider `oauthErrorDescription`. Both runtime-log assertions use a Logback `ListAppender` attached to the `MailFetcherService` logger and assert against `getFormattedMessage()`. |
| T2 | `ecm-core/src/test/java/com/ecm/core/integration/oauth/OAuthCredentialServiceTest.java` (extended) | 2 new tests: `revokeIllegalStateExceptionDoesNotEmbedErrorDescription` and `refreshIllegalStateExceptionDoesNotEmbedErrorDescription`. Both exercise the actual failure path via `MockRestServiceServer`, returning a 400 with a non-already-invalid OAuth `error` code plus an `error_description` carrying PII-shaped content; assert the thrown `IllegalStateException.getMessage()` includes the standard error code but excludes every fragment of the description. |
| T3 | `ecm-core/src/test/java/com/ecm/core/integration/mail/controller/MailAutomationControllerOAuthCallbackLoggingTest.java` (new) | 1 test: drives `controller.oauthCallback(...)` with a mocked `oauthService.handleCallback(...)` that throws `RuntimeException("provider rejected code=secret_authz_code_xyz123 for user@example.com tenant-7")`. Asserts the captured WARN log includes the exception class name `RuntimeException` and the literal `"message redacted"` marker, and excludes the authorization code, the PII content, and the exception message body. Also asserts `event.getThrowableProxy()` is null — i.e. the change drops the Throwable from the SLF4J call entirely, preventing stack-trace-borne leaks. |

## Out of scope (deliberately deferred)

- **Generic `Exception.getMessage()` at `MailFetcherService.java:179, :384`** — these catch arbitrary failures (network, parser, library). The exception class identity is unknown statically and the message content is unverified. Per user direction, do not expand the slice without an existing test that locks the new behavior.
- **~18 DEBUG-level mail-parsing exception logs** in `MailFetcherService` (lines `:477, :485, :530, :827, :2033, :2396, :2404, :2435, :2444, :2462, :2470, :2647, :2672, :2684, :2696`, etc.) — DEBUG is off by default in production; defensive cleanup here is speculative without a same-slice test.
- **`updateAccountFetchStatus(account, "ERROR", e.getMessage())` at `MailFetcherService.java:174, :180`** — this is a persistence path (the message is written to `MailAccount.lastFetchError` for later UI display via the diagnostics page), not a logger emission. UI-display redaction is a separate consideration with its own gate (it is intentionally surfaced to operators).
- **`MailReportScheduledExportService.java:106, :136`**, **`CollaboraDiscoveryService.java:191`**, **`WorkflowDeploymentRunner.java:63, :83`** — single-occurrence NEEDS-MASK sites at WARN level. Low blast radius; fold into a follow-up slice only if Phase 1 findings ever escalate.
- **Logback `RedactingConverter` infrastructure** — would centralize masking across all loggers but adds an abstraction layer for a small remediation count. Out of scope unless a future audit finds more leak patterns repetitive enough to warrant the abstraction.
- **Frontend changes** — no UI surface is affected by this slice.

## Verification

### Local static hygiene

```bash
git diff --check -- . ':!.env'
```

Expected: pass. Confirmed at time of writing.

### Local targeted Maven

```bash
./ecm-core/mvnw -f ecm-core/pom.xml -pl . \
  -Dtest='OAuthCredentialServiceTest,MailFetcherServiceLoggingRedactionTest,MailAutomationControllerOAuthCallbackLoggingTest' \
  test
```

Expected outcome on this dev box: **blocked** by missing Docker socket. The wrapper script (`ecm-core/mvnw`) routes through a Maven Docker image; absent a Docker daemon, it exits before Maven launches. Recorded blocker text:

```text
failed to connect to the docker API at unix:///Users/chouhua/.docker/run/docker.sock; check if the path is correct and if the daemon is running: dial unix /Users/chouhua/.docker/run/docker.sock: connect: no such file or directory
```

Per the established pattern across the prior 12 backend-contract slices, CI is the authoritative execution gate for this slice.

### Expected CI Gate (7 jobs)

- Backend Verify
- Frontend Build & Test
- Phase C Security Verification
- Acceptance Smoke (3 admin pages)
- Property Encryption Closeout Gate
- Phase 5 Mocked Regression Gate
- Frontend E2E Core Gate

If CI is green, append a `CI Follow-Up` section to this doc with the run id and head SHA, and commit a `[skip ci]` close-out for this slice.

## Commit sequence (per gate's instruction)

The four prior audit docs (NEXT_TRACK_DISCOVERY, AUDIT_DESIGN, AUDIT_FINDINGS, this PHASE2_REMEDIATION) plus the source + tests are split into four commits to mirror the gate's review boundaries:

| # | Type | Content | CI? |
|---|---|---|---|
| 1 | `docs(core):` | All four audit docs land together: the discovery (with #1 WITHDRAWN), the audit design brief, the findings + Phase 1.5 appendix, and this Phase 2 remediation design. Plus a short Phase 2 follow-up section in the findings doc pointing here. | Triggers CI but no production change in this commit |
| 2 | `fix(core):` | Production code changes (3 files) + new and extended test files (3 files). | Triggers CI; this is the head used to gate. |
| 3 | `docs(core):` | This doc — write a `Local Verification` section recording the `git diff --check` result + the Docker-blocked `mvnw` outcome. | Triggers CI on push but expected to pass alongside commit 2. |
| 4 | `docs(core): ... [skip ci]` | Append CI Follow-Up section to this doc with the green run id. | `[skip ci]` — no new CI run. |

Splitting docs from code is the user's preferred boundary for this slice; combining all into one commit would still pass CI but obscure the audit-then-fix narrative.

## Local Verification

| Check | Status |
|---|---|
| `git diff --check -- . ':!.env'` | passed |
| Production changes touch only the three files in scope | confirmed via `git diff --stat -- ecm-core/src/main/java/` |
| Test changes touch only the three files in scope | confirmed via `git diff --stat -- ecm-core/src/test/java/` + new test files staged |
| `.env` not modified | confirmed (`M .env` is the pre-existing local override, untouched by this slice) |
| `./ecm-core/mvnw -Dtest=... test` | blocked by missing Docker socket (recorded above) — CI is the execution gate |

## CI Follow-Up

Populated after CI completes.

- GitHub Actions run: `<pending>`
- Head: `<pending>`
- Result: `<pending>`
