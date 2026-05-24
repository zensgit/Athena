# Sensitive-Data Logging Audit — Phase 2 Follow-up #1 (OAuth Exception Cause Chain)

Date: 2026-05-23

## Context

Phase 2 (commit `f80bd8d` + align `f604b1b`, CI run `26339319405`) modified the `IllegalStateException` message construction in `OAuthCredentialService` revoke and refresh failure paths to exclude `parsed.errorDescription()`. The tests asserted `IllegalStateException.getMessage()` does not contain the provider error description.

**Gate finding (2026-05-23):** the fix did not cover the cause chain. The `IllegalStateException` was still constructed with the raw `HttpStatusCodeException` (`HttpClientErrorException`, `HttpServerErrorException`, etc.) as cause. Spring's `RestClientResponseException.getMessage()` includes the response body verbatim (e.g. `400 Bad Request: "{\"error\":\"invalid_request\",\"error_description\":\"User user@example.com violated scope-x\"}"`). When `RestExceptionHandler.handleInternalState` (`:44`) emits `log.error("...", request.getRequestURI(), ex.getMessage(), ex)`, SLF4J/Logback walks the cause chain via `Throwable.printStackTrace`-style serialization and emits the full body — including `error_description` — into the ERROR log.

The Phase 2 doc even acknowledged this in writing: *"the description remains accessible via the cause chain (ex)"*. That is exactly the log path Phase 2 was supposed to close.

## Scope (production code changes)

| File | Change |
|---|---|
| `ecm-core/src/main/java/com/ecm/core/integration/oauth/OAuthCredentialService.java` | Add private static helper `sanitizedHttpCause(HttpStatusCodeException ex)` that returns a new `RuntimeException` carrying only the exception class simple name + HTTP status code, with the original stack frames copied. The response body is dropped. Update **four** `throw new IllegalStateException(message, cause)` sites to wrap their `HttpStatusCodeException`/`HttpServerErrorException` cause through this helper: `:222` (revoke 5xx), `:229` (revoke 5xx variant), `:252` (revoke parsed-4xx), `:395` (refresh non-`invalid_grant`). |

`ResourceAccessException` site (`:257`) is intentionally **not changed** — it represents a transport error (timeout, connection refused) where no response body exists, so the original exception is body-safe and the stack frames are useful for ops.

## Tests extended

| File | Change |
|---|---|
| `ecm-core/src/test/java/com/ecm/core/integration/oauth/OAuthCredentialServiceTest.java` | Extend the two Phase 2 tests `revokeIllegalStateExceptionDoesNotEmbedErrorDescription` and `refreshIllegalStateExceptionDoesNotEmbedErrorDescription` with a full `Throwable.printStackTrace(PrintWriter)` capture into a `StringWriter`. Assert the serialized stack-trace emission excludes every fragment of the provider `error_description` (e.g. `user@example.com`, `scope-x`, `violated`, `Q4 layoff plan`, `rejected`). This is the regression-lock that pre-follow-up tests missed: the message-only assertions would have passed even with the leaking cause. |

The `printStackTrace(PrintWriter)` serialization mirrors what SLF4J / Logback emit at `RestExceptionHandler:44` when `log.error("...", ex)` is called with the IllegalStateException — same recursive cause-chain walk, same "Caused by:" emission. Asserting against this serialization is equivalent to attaching a `ListAppender` to `RestExceptionHandler.class` and triggering the actual MVC error path, but does not require bringing up a Spring MVC test slice.

## Out of scope (intentional, deferred from Phase 2)

These sites carry the same root-cause pattern (raw `HttpStatusCodeException` reaches a logger) but are deferred per the Phase 2 OOS list and the user's "do not expand without same-slice test" discipline:

- `OAuthCredentialService.java:425` (or wherever the refresh path rethrows raw `HttpStatusCodeException` when `parsed == null` — `throw ex;` propagates the original exception). This flows to `MailFetcherService:179`'s generic `catch (Exception e)` and is emitted via `log.error("Failed to process mail account: {}", account.getName(), e)`. Distinct log site, distinct exception type, distinct fix scope. The Phase 2 OOS list already names `MailFetcherService:179` as out-of-scope; this is the same leak path. Open as Follow-up #2 only if a future gate finding promotes it.
- `MailFetcherService:179, :384` and the ~18 DEBUG-level mail-parsing exception logs — same as Phase 2 OOS.
- `MailFetcherService:175` `updateAccountFetchStatus(account, "ERROR", e.getMessage())` — persistence path (the message is stored on `MailAccount.lastFetchError` and rendered in the admin UI). Not a logger emission; UI-display redaction is a separate gate question.
- Logback `RedactingConverter` infrastructure — same as Phase 2 OOS.

## Why this fix is correct (not just defensive)

The sanitizer **preserves** the failure-point context that matters for ops:

- The original stack frames are copied via `setStackTrace(ex.getStackTrace())`, so the "Caused by:" emission still points to the exact frame inside Spring's `RestTemplate` where the HTTP exception was thrown. Stack-trace-driven debugging continues to work.
- The class simple name (e.g. `HttpClientErrorException$BadRequest`) is preserved in the synthesized cause's `getMessage()`, so the operator sees what HTTP-exception subclass was raised and can correlate with Spring's exception hierarchy.
- The HTTP status code is preserved, distinguishing 4xx from 5xx from "parse failed" cases.

The sanitizer **drops** only the response body — which is the leak source.

The `IllegalStateException`'s own `getMessage()` was already sanitized in Phase 2 to include `parsed.error()` (the OAuth standard code, e.g. `invalid_grant`) but exclude `parsed.errorDescription()`. Combined, the emission now carries:

- IllegalStateException message: `"OAuth token revoke failed for owner X: invalid_request"`
- IllegalStateException's own stack (starts at the throw site in OAuthCredentialService)
- "Caused by:" the sanitized cause: `"RuntimeException: HttpClientErrorException (HTTP 400)"`
- Cause's stack (copied from the original HttpStatusCodeException, pointing into Spring RestTemplate)

No fragment of `parsed.errorDescription()` reaches the log sink at any of these layers.

## Verification

Local static hygiene: `git diff --check -- . ':!.env'` passed.

Local Maven targeted test: blocked by missing Docker socket on this dev box (same as past 12+ slices). CI is the authoritative execution gate.

### Expected CI gate

- Backend Verify (must pass — these are pure-Java unit tests)
- Frontend Build & Test
- Phase C Security Verification
- Acceptance Smoke (3 admin pages)
- Property Encryption Closeout Gate
- Phase 5 Mocked Regression Gate
- Frontend E2E Core Gate

If CI is green, append a `CI Follow-Up` section to this doc with the run id + head SHA.

## Commit sequence

| # | Type | Content |
|---|---|---|
| 1 | `fix(core):` | Production sanitizer helper + 4 throw-site updates + 2 extended tests with full-stack-trace assertion. |
| 2 | `docs(core):` | This follow-up doc + Phase 2 status updates in findings doc and Phase 2 remediation design doc. |
| 3 | `docs(core): ... [skip ci]` | CI Follow-Up section with the green run id. |

## CI Follow-Up

Populated after CI completes.

- GitHub Actions run: `<pending>`
- Head: `<pending>`
- Result: `<pending>`
