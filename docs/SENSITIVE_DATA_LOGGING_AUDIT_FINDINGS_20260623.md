# Sensitive-Data Logging Audit — Findings (Phase 1, read-only)

Date: 2026-06-23. Method/taxonomy: `SENSITIVE_DATA_LOGGING_AUDIT_DESIGN_20260523.md` + the re-grounded
lanes in `SENSITIVE_DATA_LOGGING_AUDIT_PHASE1_TASKBOOK_20260623.md`. **Read-only — no code changed.**
Two ratified criteria applied: (1) a single HIGH-RISK NEEDS-MASK can trigger Phase 2 on its own;
(2) `log.X(..., throwable)` sites are judged against the FULL throwable + cause chain, not the top message.

## Summary

| Lane | Path | Sites | SAFE | NEEDS-MASK | LEAKS | of which HIGH-RISK |
|---|---|---:|---:|---:|---:|---:|
| OAuth | `integration/oauth` + `OAuthCredentialAdminController` | **0** | 0 | 0 | 0 | 0 |
| Secret/crypto | `security/secret` | **0** | 0 | 0 | 0 | 0 |
| Transfer | `service/TransferReplication*` + `service/transfer` | 6 | 3 | 3 | 0 | **1** |
| Mail | `integration/mail/**` | ~65 | ~31 | ~30 | 0 | **~6** |
| WOPI | `integration/wopi` | 2 | 1 | 1 | 0 | 0 |
| LDAP | `integration/ldap` | 6 | 5 | 1 | 0 | 0 |
| Security (non-secret) | `security/**` | 4 | 3 | 1 | 0 | **1** |
| config | `config` | 5 | 3 | 2 | 0 | 0 |
| exception | `exception` | **0** | 0 | 0 | 0 | 0 |
| **Total** | | **~88** | **~46** | **~38** | **0** | **~8** |

- **LEAKS: 0** — no site statically interpolates a named secret/token/password.
- **`System.out`/`System.err`: 0** across all in-scope lanes.
- **NEEDS-MASK ~38, of which ~8 HIGH-RISK** → **Phase 2 GATE IS MET** (both the count >10 and the single-high-risk criteria). See §Recommendation.
- **Severity-ladder split (the on-in-prod surface is bounded):** ~18 of the NEEDS-MASK are `log.debug` (OFF in production by default — lowest priority: mail content/decode/IO failures). The remaining ~20 are `WARN`/`ERROR` (ON in prod), and **all ~8 HIGH-RISK are WARN/ERROR**. So Phase 2's on-in-prod target is ~20 sites, headed by the confirmed `:376`.
- **One CONFIRMED body-to-log mechanism (`:376`, LEAKS-adjacent):** an unbounded remote HTTP response body reaches a WARN log + the DB `transportMessage` (verified — default `RestTemplate`, un-caught `exchange()`). Counted as HIGH-RISK rather than LEAKS only because the body is not a *named* secret, but it is the single must-fix.

## Good defensive baseline (context — the codebase is NOT careless)
The obvious sensitive spots already redact: `MailAutomationController:368` logs `(message redacted)` + only the
exception class; `MailFetcherService:966` uses `redactSubjectForLog(...)`; `:748` logs `getClass().getSimpleName()`;
`:3296` notes "non-ASCII password detected" **without** logging the password; OAuth deliberately **drops the
exception cause** before any log emission (`OAuthCredentialService` comments L161-167); no custom `toString()`
on any sensitive DTO; OAuth + Secret lanes log **nothing at all**. So the NEEDS-MASK population is mostly
"exception message/throwable logged without a positive proof it is safe", not active leaking.

## HIGH-RISK NEEDS-MASK (drives Phase 2)

| File:line | Level | Pattern | Why high-risk |
|---|---|---|---|
| `service/TransferReplicationService.java:376` | warn | `"Replication job {} failed: {}", jobId, ex.getMessage()` | **CONFIRMED mechanism (verified, not hypothetical):** `AthenaTransferHttpClient` uses the **default `RestTemplate`** (`config/RestTemplateConfig.java:12` = `new RestTemplate()`, NO custom `ResponseErrorHandler`) and does **NOT** try-catch its `restTemplate.exchange()` calls (the file's only catch, :319, is a local `IOException`). So a 4xx/5xx from the remote receiver makes the default handler throw `HttpClientErrorException`, whose `getMessage()` includes the **remote response-body excerpt** (Spring default), which propagates raw to `:376`. Logged at WARN (ON in prod) AND persisted to `job.transportMessage` (:389) + failure report (:384). Body = the remote receiver's error response (internal detail / node-folder names — not a named credential, but **unbounded remote content in logs + DB**). **Strongest finding; LEAKS-adjacent.** |
| `security/mfa/TotpService.java:82` | error | `"Failed to compute HMAC", e` | MFA/TOTP **crypto** path; full throwable logged. An HMAC/key exception's cause chain could embed key material. Verify the JCA exception does not carry the seed. |
| `integration/mail/service/MailFetcherService.java:184` | error | `"Failed to process mail account: {}", account.getName(), e` | full throwable for an account-connect/auth failure; cause chain (IMAP/OAuth) could embed connection/credential detail. |
| `…/MailFetcherService.java:2492` | warn | `"Failed to update mail properties for document {}", documentId, e` | full throwable; cause chain unverified. |
| `…/MailFetcherService.java:3096` | warn | `"Failed to update fetch status for account {}", account.getName(), e` | full throwable; cause chain unverified. |
| `…/MailFetcherService.java:966` | error | `"Error processing message: {}", redactSubjectForLog(message), e` | subject IS redacted (good) but the throwable `e` is still logged in full — cause chain unverified. |
| `…/MailReportScheduledExportService.java:165` | warn | `"Mail report scheduled export errored", ex` | full throwable; export path may wrap transfer/IO causes. |
| `…/MailFetcherService.java:1079` | warn | `"Mail connection test failed for {}: {}", account.getName(), message` | `message` derived from a mail-connection failure; could embed host/auth detail. |

## NEEDS-MASK (standard — `e.getMessage()` of mail/IO ops, low–medium, Phase-2 positive-proof)
Mail content/decode/IO failures logging `e.getMessage()` at debug/warn: `MailFetcherService` :398, :491, :499,
:544, :550, :562, :670, :694, :759, :995, :1007, :2262, :2274, :2305, :2668, :2676, :2707, :2716, :2734, :2742,
:2919, :2955, :2980, :2992; `MailReportScheduledExportService:135`. Plus `TransferReplicationService` :269, :291
(internal/executor `RuntimeException.getMessage()`, no HTTP body — likely SAFE on proof); `WopiService`→
`CollaboraDiscoveryService:191` (discovery-load `e.getMessage()`); `JndiLdapDirectoryClient:345` (context-close
`e.getMessage()`, benign); `WorkflowDeploymentRunner:63, :83` (workflow-resource throwables, no secret).
Reason held at NEEDS-MASK: the exception type/cause is not statically proven to exclude content/PII; several log
subject/sender/recipient decode errors whose message may embed the raw value (PII).

## SAFE (representative — id/count/name/status/redacted)
Transfer :484 + Scheduler :20,:32 (IDs/counts); Mail runId/account-name/folder/UID/count/rule-name summaries
(:133,:195,:306,:404,:436,:575,:920,:946,:953,:985,:1023,:1031,:1034,:1043,:1988,:2086,:2140,:3066, retention
:53,:61,:64,:66, export :84,:109,:157, redacted :368,:748,:3296); WOPI :113 (docId+size); LDAP :24,:53,:85,:135,
:196 (counts + DNs); security :42,:50,:59 (identity debug: username + node id); config :48,:68,:73 (basePath+count).

## Indirect-leak vectors (design Step 4)
- **`toString()` overrides in sensitive packages: NONE** — logging a domain object won't auto-expand a secret.
- **`System.out`/`System.err`: 0** in-scope.
- **Exception subclasses: 2** — `OAuthReauthRequiredException`, `MailOAuthReauthRequiredException` (both `extends RuntimeException`); their `super(message)` is a reauth signal — Phase 2 should confirm the message carries no token (low risk).

## Honest gaps
- Strict regex `(log|logger|LOG|LOGGER)\.(level)\(`; a fluent/wrapper logger (`log.atInfo()...`, `auditLog.record`) or a non-`log` Logger var would be missed — none observed, but not exhaustively proven.
- `e.getMessage()` sites are held at NEEDS-MASK WITHOUT deep per-exception-type tracing (design-sanctioned: "leave NEEDS-MASK with reason" → Phase 2 positive-proof). The HIGH-RISK subset is where tracing matters most.
- A few mail multi-line `log.warn` (e.g., :173, :2216) were classified from the format string; their exact args weren't fully expanded (info/warn summaries, low risk).
- Per-tenant/PII (subject, sender, recipient) is treated as NEEDS-MASK content, not a hard LEAK; the owner may set a stricter PII policy.

## Recommendation — Phase 2 OPENS (gate met)
0 confirmed LEAKS and 0 `System.out/err`, BUT the NEEDS-MASK population (~38) and the **~8 HIGH-RISK cause-chain
sites** meet both ratified gate criteria. Proposed Phase-2 slices (per the design's per-slice + CI-gate pattern),
ordered by risk:
1. **Transfer `:376` (CONFIRMED) — mask the transfer HTTP error to status-only.** The default `RestTemplate`
   (`RestTemplateConfig.java:12`) + un-caught `exchange()` in `AthenaTransferHttpClient` lets
   `HttpClientErrorException` (body excerpt in `getMessage()`) reach the WARN log + `transportMessage` + failure
   report. Fix: add a `ResponseErrorHandler` to the transfer `RestTemplate`, OR catch `RestClientResponseException`
   in `AthenaTransferHttpClient` and rethrow a sanitized (status-only) exception. Highest blast radius (logs + DB).
   Add a log/DB-capture regression test asserting the body excerpt is absent.
2. **MFA `TotpService:82`** — confirm/strip any key material from the HMAC exception before logging.
3. **Mail full-throwable sites** (:184, :966, :2492, :3096, report :165) — apply the existing OAuth "drop/sanitize
   cause" pattern so IMAP/OAuth cause chains can't emit credentials/bodies; add a log-capture regression test.
4. **Mail content/decode `e.getMessage()`** (subject/from/recipient/body) — mask to class-name or a bounded
   redaction where the message can embed PII/content.

If the owner judges the cause-chain risk acceptable as-is (given the existing redaction baseline), the track can
instead close with this doc + a targeted fix of only slice 1+2. **Owner decides Phase 2 scope.**
