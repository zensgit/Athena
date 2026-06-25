# Sensitive-Data Logging Audit - Development and Verification Closeout

Date: 2026-06-24
Repository: Athena
Status: Complete for the ratified high-risk audit/remediation line. Remaining low/medium `NEEDS-MASK`
sites are documented backlog, not part of this completed slice.

## Objective

The one-week development lane started by checking whether the license/entitlement admin surface was the
right next small build. The code audit showed the current license subsystem is a non-enforcing placeholder,
so that work was closed as honest documentation instead of UI polish. The main build lane then moved to
the previously identified sensitive-data logging audit.

This closeout records:

- the license/entitlement status correction that prevented building on a mock subsystem;
- the Phase 1 sensitive-data logging findings;
- the Phase 2 remediation slices for the highest-risk sinks;
- the verification evidence used to close the lane.

## Delivered Work

| PR | Commit | Scope | Result |
|---|---|---|---|
| #27 | `69bcd7a` | License/entitlement gap audit and `FEATURE_LICENSING.md` correction | Merged. Licensing is documented as placeholder/non-enforcing. |
| #28 | `a110e4d` | Sensitive-data logging Phase 1 taskbook and findings | Merged. Phase 2 gate opened by high-risk `NEEDS-MASK` sites. |
| #29 | `2cc107f` | Transfer replication HTTP error sanitization | Merged. Remote response body no longer reaches transfer logs or persisted job failure fields. |
| #30 | `6a406ef` | TOTP HMAC failure logging | Merged. Crypto-path HMAC failure logs exception type only, no attached throwable. |
| #31 | `7866fc5` | Mail high-risk error sinks | Merged. Five full-throwable mail log sites and three same-source persisted error sinks now use type/code-only output. |

This branch adds the closeout polish:

- aligns the `MailFetcherServiceLoggingRedactionTest` simulated message-processing log with the current
  production type-only format;
- adds a real account-processing failure test for `MailFetcherService` `:184/:185`, proving both the log
  and `lastFetchError` store exception type only;
- records this development and verification closeout.

## Phase 1 Findings Summary

Source: `docs/SENSITIVE_DATA_LOGGING_AUDIT_FINDINGS_20260623.md`.

Important audit rules:

- `log.X(..., throwable)` was evaluated against the full throwable/cause chain, not just the top-level
  message.
- A single high-risk `NEEDS-MASK` site could open Phase 2 even without a named secret leak.
- `System.out` and `System.err` were checked for the in-scope sensitive lanes.

Phase 1 result:

- `LEAKS`: 0
- `System.out`/`System.err`: 0 in scope
- `NEEDS-MASK`: about 38, including about 8 high-risk sites
- Phase 2 gate: met

The strongest confirmed finding was `TransferReplicationService:376`: default `RestTemplate` HTTP errors
could carry a remote response-body excerpt into the WARN log and persisted transfer failure fields.

## Remediation Summary

### Slice 1 - Transfer HTTP Errors

Files:

- `ecm-core/src/main/java/com/ecm/core/service/transfer/AthenaTransferHttpClient.java`
- `ecm-core/src/test/java/com/ecm/core/service/transfer/AthenaTransferHttpClientTest.java`

Behavior:

- catches remote transfer HTTP errors at the client boundary;
- converts them to status-only failure text;
- prevents response body excerpts from reaching logs, `transportMessage`, `errorLog`, or entry reports.

### Slice 2 - TOTP HMAC Failure

Files:

- `ecm-core/src/main/java/com/ecm/core/security/mfa/TotpService.java`
- `ecm-core/src/test/java/com/ecm/core/security/mfa/TotpServiceHmacFailureLoggingTest.java`

Behavior:

- replaces full throwable logging with exception type-only logging;
- keeps the thrown application exception behavior unchanged;
- locks the log shape with a `ListAppender` assertion that no `ThrowableProxy` is attached.

### Slice 3 - Mail Error Sinks

Files:

- `ecm-core/src/main/java/com/ecm/core/integration/mail/service/MailFetcherService.java`
- `ecm-core/src/main/java/com/ecm/core/integration/mail/service/MailReportScheduledExportService.java`
- `ecm-core/src/test/java/com/ecm/core/integration/mail/service/MailFetcherServiceLoggingRedactionTest.java`
- `ecm-core/src/test/java/com/ecm/core/integration/mail/service/MailReportScheduledExportServiceTest.java`

Behavior:

- converts high-risk mail full-throwable logs to type-only output:
  - account processing failure;
  - message processing failure;
  - mail document property update failure;
  - fetch-status persistence failure;
  - scheduled report export failure;
- converts adjacent persisted/admin-visible error sinks to code/type-only:
  - OAuth reauth `lastFetchError` stores OAuth code only;
  - generic account failure `lastFetchError` stores exception type only;
  - scheduled export failure result stores exception type only.

Positive-proof result before remediation: none of the five mail log sites was promoted to a confirmed
content leak, but none was provably safe while emitting a full throwable. Type-only remediation was the
smallest consistent fix.

## Verification Evidence

GitHub checks:

- #28: 7/7 checks passed before merge.
- #29: 7/7 checks passed before merge.
- #30: 7/7 checks passed before merge.
- #31: 7/7 checks passed before merge.

Local targeted verification command:

```bash
cd ecm-core
./mvnw -q -Dtest=AthenaTransferHttpClientTest,TotpServiceHmacFailureLoggingTest,MailFetcherServiceLoggingRedactionTest,MailReportScheduledExportServiceTest test
```

Result before this closeout patch: passed. It also exposed a stale simulated test log shape for the
message-processing mail site; this branch corrects that test to match the current type-only production
format and adds the missing account-processing failure proof.

Expected proof after this closeout patch:

- transfer HTTP response body fragments do not reach transfer failure messages;
- TOTP HMAC failure log has no attached throwable;
- mail message-processing log has no attached throwable and no raw subject/cause message;
- mail account-processing failure log has no attached throwable and no provider/cause message;
- mail generic account failure stores `RuntimeException` instead of `e.getMessage()`;
- mail OAuth reauth failure stores the OAuth error code, not provider `errorDescription`;
- mail scheduled export failure stores/logs exception type only.

## Remaining Backlog

The following are intentionally not part of this closed high-risk line:

- lower-priority mail `e.getMessage()` `WARN`/`DEBUG` sites from the Phase 1 findings;
- broader PII policy decisions for subject, sender, recipient, and message-content diagnostics;
- library/framework logging controls outside `com.ecm.*`;
- turning the placeholder license subsystem into real license enforcement.

These should be scheduled only if product/security policy requires them. They are not blockers for this
completed sensitive-data logging audit high-risk track.

## Closeout Decision

The selected one-week lane is complete once this closeout patch passes the targeted backend tests:

- the mock license-admin idea was corrected into documentation rather than misleading UI;
- the sensitive-data logging Phase 1 audit is stored in docs;
- every ratified high-risk remediation slice is merged on `main`;
- the remaining lower-risk `NEEDS-MASK` population is explicitly documented as backlog.
