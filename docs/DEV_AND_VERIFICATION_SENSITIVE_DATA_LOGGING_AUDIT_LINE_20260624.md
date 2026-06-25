# Sensitive-Data Logging Audit — Line Development & Verification

Date: 2026-06-24. Scope: the Athena ECM sensitive-data logging audit line — Phase 1 (read-only
audit → findings) + Phase 2 (confirmed / high-risk remediation). Everything below is merged to
`main` and CI-green. Cumulative record; one deliverable per the design-lock → ratify → build loop.

## 1. State at a glance

**The line is COMPLETE on main.** 0 confirmed named-secret leaks, 0 `System.out`/`System.err`. Every
**confirmed** and **high-risk full-throwable** log site (and its adjacent persisted error message) is now
**type-only**. The residual (mostly `log.debug`, OFF in production) is deliberately accepted and documented
(§6) — not silently unfinished.

| PR | What | Merge |
|---|---|---|
| #28 | Phase 1 — findings doc + re-grounded taskbook | `a110e4d` |
| #29 | Slice 1 — Transfer `:376` (the one CONFIRMED body→log/DB chain) | `2cc107f` |
| #30 | Slice 2 — TOTP `:82` (MFA crypto-path throwable) | `6a406ef` |
| #31 | Mail track — 5 logs + 3 persisted sinks → type-only | `7866fc5` |

## 2. Context — why this line

- `PROPERTY_ENCRYPTION_CLOSEOUT_TODO` §449 and `OAUTH_CREDENTIAL_STORE_CLOSEOUT_TODO` both demanded
  "no plaintext / key material in logs", but neither closeout ran a systematic logger-call-site audit;
  both shipped to production. `NEXT_TRACK_DISCOVERY_20260523` picked this as the next track on the
  operational-risk axis.
- Load-bearing mechanism: SLF4J `log.X("...", throwable)` emits the **entire cause chain** (messages +
  stacks) via `Throwable.printStackTrace`; and `e.getMessage()` of a body-bearing exception (Spring's
  `HttpClientErrorException`) embeds the HTTP **response body**. So "full throwable" and bare
  `e.getMessage()` are the two leak shapes the audit hunts.

## 3. Phase 1 — read-only audit (PR #28)

- **Scope:** ~88 production `Logger` call-sites across `integration/oauth`, `security/secret`,
  `service/transfer` + `TransferReplication*`, `integration/mail`, `integration/wopi`, `integration/ldap`,
  `security/**`, `config`, `exception`. The 2026-05-23 design's lane PATHS had drifted; the
  `SENSITIVE_DATA_LOGGING_AUDIT_PHASE1_TASKBOOK_20260623.md` re-grounded them against current code first.
- **Method:** classify each site SAFE / NEEDS-MASK / LEAKS, plus two ratified criteria — (1) a single
  HIGH-RISK NEEDS-MASK can trigger Phase 2 on its own; (2) `log.X(..., throwable)` is judged against the
  full throwable + cause chain — plus indirect-leak checks (`toString()`, exception `super(message)`,
  `System.out/err`).
- **Result:** 0 named-secret LEAKS, 0 `System.out/err`; ~46 SAFE, ~38 NEEDS-MASK (~18 are `debug`, off
  in prod), ~8 HIGH-RISK; **one CONFIRMED body-to-log mechanism** (`TransferReplicationService:376`).
- **Good defensive baseline already present** (not careless code): redaction in the obvious spots
  (`MailAutomationController:362` "(message redacted)", `MailFetcherService:966` `redactSubjectForLog`,
  `:748` class-name, `:3296` doesn't log the password); `oauth` + `secret` lanes log nothing at all; no
  custom `toString()` on a sensitive DTO; `System.out/err` = 0.
- **Records:** `docs/SENSITIVE_DATA_LOGGING_AUDIT_FINDINGS_20260623.md` (per-site classification, indirect-leak
  findings, honest gaps, Phase-2 recommendation) + the taskbook. Method/taxonomy in the 2026-05-23 design.

## 4. Phase 2 — remediation slices

### Slice 1 — Transfer `:376` (PR #29) — the CONFIRMED chain
- **Finding (verified):** `AthenaTransferHttpClient` uses the default `RestTemplate` (`RestTemplateConfig:12`,
  no `ResponseErrorHandler`) and does NOT catch its `exchange()` calls → on 4xx/5xx the default handler
  throws a raw `HttpClientErrorException` whose `getMessage()` embeds the remote response-body excerpt,
  which propagates to `TransferReplicationService:376` (`log.warn(... ex.getMessage())`) and is persisted
  to `transportMessage` / `errorLog` / the failure entry report — logs AND DB.
- **Fix:** sanitize at the **transfer-client boundary** (NOT a global handler — the shared `RestTemplate`
  used by WOPI / preview / ML stays unaffected): the three `exchange()` calls go through `exchangeJson()`,
  which catches `RestClientResponseException` and rethrows a **status-only** `IllegalStateException`
  (operation + HTTP status; sanitized stand-in cause with the stack copied; no body). Mirrors
  `OAuthCredentialService.sanitizedHttpCause`. `ResourceAccessException` (no response, no body) is left to
  propagate.
- **Test:** `AthenaTransferHttpClientTest.remoteHttpErrorBodyIsSanitized` — a 500 + sensitive body yields a
  thrown exception whose **message AND full `printStackTrace`** carry `HTTP 500` + the operation but not the body.

### Slice 2 — TOTP `:82` (PR #30) — MFA crypto path
- **Finding:** `log.error("Failed to compute HMAC", e)` logged the full Throwable in the HmacSHA1 / TOTP-secret
  path (a JCA exception's cause chain could, in principle, carry key/format detail).
- **Fix:** type-only — `log.error("Failed to compute HMAC: type={}", e.getClass().getSimpleName())`. Mirrors
  `MailAutomationController:362`. The `:83` rethrow is left as-is (JCA exceptions are not body/key-bearing
  like the HTTP case; out of scope).
- **Test:** `TotpServiceHmacFailureLoggingTest` — a `ListAppender`, the failure triggered via an empty key,
  asserts the log event keeps the type but has **no attached Throwable**.

### Mail track — 5 logs + 3 persisted sinks (PR #31)
- **Positive-proof first (read-only):** each of the 5 high-risk sites logs a full Throwable of a mail/DB
  operation; the messages *could* carry mail content / connection detail but **none is a confirmed
  content-leak chain**. The genuine PII risk — the OAuth-reauth provider `errorDescription` (proven to
  carry `user@example.com` / tenant by the existing test) — was already kept out of the **log** but was
  still **persisted** to `lastFetchError` (admin-UI visible) via `e.getMessage()`. Logs-only would leave
  that half-open → scope (b): logs + same-source sinks together.
- **Fix (type-only):** logs — `MailFetcherService` :184, :966 (subject already redacted), :2492, :3096;
  `MailReportScheduledExportService` :165. Sinks — `MailFetcherService` :179 (OAuth reauth → **code-only**,
  so the provider description PII is not saved to `lastFetchError`), :185 (generic → type); 
  `MailReportScheduledExportService` :163 (failed result → type). No broad mail sanitizer; the other ~30
  NEEDS-MASK (mostly `debug`, off in prod) are untouched.
- **Test:** real `ListAppender` triggers of `:2492` / `:3096` (mocked dependency throws) and the `:165`
  export failure assert **no `ThrowableProxy`** and the sensitive cause string absent; the export result
  message and the OAuth sink keep only the type/code, never the thrown message / provider PII.

## 5. Verification

- **CI:** every PR merged with all **7 gates green** (Backend Verify, Frontend Build & Test, Frontend E2E
  Core Gate, Phase 5 Mocked Regression Gate, Phase C Security Verification, Property Encryption Closeout
  Gate, Acceptance Smoke). Squash scopes verified per merge.
- **Slice tests** (above) lock the no-throwable / type-only behavior at the production call sites, using the
  codebase's established Logback `ListAppender` pattern (`MailAutomationControllerOAuthCallbackLoggingTest`).
- **Honest process note:** the dev box has no Docker / Maven / `.m2-cache` and the egress proxy blocks the
  Apache mirror, so local Maven could not run. CI's Backend Verify is the compile/test gate — and it earned
  its keep: it caught a real `testCompile` error on the mail slice (`MailReportScheduledExportServiceTest`
  missing `throws Exception`), fixed directly from the CI log and re-verified green.

## 6. Final state + deliberate scope boundaries

- **Done:** the one CONFIRMED chain (transfer `:376`), the MFA crypto throwable (TOTP `:82`), and the mail
  high-risk full-throwable logs + adjacent persisted messages — all type-only. 0 confirmed named-secret
  leak; 0 `System.out/err`.
- **Deliberately NOT touched (accepted):** ~18 `log.debug` NEEDS-MASK (off in production) + the remaining
  low-risk `e.getMessage()` sites — no confirmed leak, covered by the existing redaction baseline; a broad
  mail/global sanitizer was explicitly declined as over-scope.
- **Honest gaps (carried from the findings doc):** the strict log-call regex could miss a fluent/wrapper
  logger (none observed); `e.getMessage()` sites are held at NEEDS-MASK without exhaustive per-exception-type
  tracing — the HIGH-RISK subset is where tracing was actually done.

## 7. Conclusion

The sensitive-data logging audit line is complete and verified on `main`: a read-only Phase 1 audit (record)
plus Phase 2 remediation of the one confirmed leak, the MFA crypto throwable, and the mail high-risk
full-throwable logs + their persisted sinks. No confirmed leak remains; the residual is the documented
low-risk / off-in-prod surface, deliberately accepted. Re-entry, if ever needed, is the broad
`debug`-level NEEDS-MASK sweep — a separate, lower-priority track, not opened here.
