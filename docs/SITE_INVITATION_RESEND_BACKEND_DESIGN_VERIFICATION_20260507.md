# Site Invitation Resend - Backend Design and Verification

Date: 2026-05-07

## Context

Site invitation infrastructure shipped in an earlier round. The previous `sendInvitationEmail` path was async fire-and-forget — failure was logged but never persisted on the invitation row, so an operator had no in-product signal that a recipient had not received the email.

This round adds:
- Per-row send-tracking columns (migration 092).
- A synchronous send variant on `EmailNotificationService` so callers can record the outcome.
- An admin/manager-only resend endpoint that re-attempts a `PENDING` invitation.

The user has explicitly decided NO automatic retry worker in v1. Every retry comes through the operator-driven resend control.

## Design

### Migration 092 — `092-add-site-invitation-send-tracking.xml`

Adds five columns to `site_invitations`:

| Column | Type | Nullable | Default |
| --- | --- | --- | --- |
| `last_send_attempt_at` | TIMESTAMP | yes | null |
| `last_send_status` | VARCHAR(20) | yes | null |
| `last_send_error` | TEXT | yes | null |
| `send_attempt_count` | INT | no | 0 |
| `last_sent_at` | TIMESTAMP | yes | null |

`last_send_status` stores `SiteInvitation.LastSendStatus` as a string enum: `SENT` | `FAILED`. NULL means "no attempt has been made yet" — there is intentionally no `NEVER` value, because the absence of any attempt is naturally null.

Rollback drops the columns in reverse order.

### `EmailNotificationService.sendSync(...)`

The legacy `@Async send(...)` was extended with a synchronous sibling that returns deterministic results:

```java
public record SendResult(boolean ok, String error) {}
public SendResult sendSync(String templateKey, String to, String preferredLocale, Map<String,Object> variables);
```

Pre-check error strings (stable for tests and for the operator runbook):

| Pre-check | Returned `error` (when `ok=false`) |
| --- | --- |
| `ecm.email.enabled` is false | `"ecm.email.enabled is false"` |
| `to` is blank | `"recipient address is blank"` |
| `ecm.email.from-address` is blank | `"ecm.email.from-address is not configured"` |
| `JavaMailSender` not in context | `"JavaMailSender is not configured"` |
| Template not found | `"template not found: <key> for locale fallback"` |
| `MailException` thrown | `"SMTP send failed: " + ex.getMessage()` |
| Any other Exception | `"unexpected send failure: " + ex.getClass().getSimpleName() + " " + ex.getMessage()` |

The legacy `send(...)` is now a thin `@Async` wrapper that calls `sendSync(...)` and ignores the result — keeping fire-and-forget semantics for callers (e.g. RM report-preset notifications) that never inspected the outcome.

### `SiteInvitationService.sendInvitationEmail` becomes synchronous

The private helper now:

1. Sets `lastSendAttemptAt = now()` and increments `sendAttemptCount` BEFORE the send call (so an unhandled crash still leaves the attempt-count truthful).
2. Calls `emailService.sendSync(...)`.
3. On `result.ok()` → `lastSendStatus = SENT`, `lastSentAt = now()`, `lastSendError = null`.
4. On `!result.ok()` → `lastSendStatus = FAILED`, `lastSendError = truncate(result.error(), 1000)`.
5. Saves the invitation row.

If `EmailNotificationService` is not in context (the `ObjectProvider` returns `null`), the row is marked `FAILED` with the error `"EmailNotificationService not available"` so the failure is still observable.

A defensive try/catch around `sendSync` handles future regressions: even though the contract says `sendSync` does not throw, if it ever did, the failure path captures the exception class and message rather than swallowing.

Trade-off: SMTP latency now sits inside the @Transactional path. A typical send takes ~1-3 s; during that window the row is held under the transaction's write lock. Without this change the failure tracking is impossible — async send-and-forget cannot relay the outcome back to the row. The trade-off is documented here for future revisits.

### `SiteInvitationService.resend(siteId, invitationId)`

Mirrors the shape of `cancel(siteId, invitationId)`:

- `loadSite(siteId)` → `ResourceNotFoundException` if unknown.
- `ensureCanManageInvitations(site, currentUser)` → admin OR site MANAGER, otherwise `SecurityException`.
- `invitationRepository.findById(invitationId).orElseThrow(ResourceNotFoundException)`.
- Cross-site guard: if `invitation.site.id != site.id` → `ResourceNotFoundException` (does not leak existence across sites).
- Status guard: if `invitation.status != PENDING` → `IllegalArgumentException` with `"Only PENDING invitations can be resent; current status: <STATUS>"`. ACCEPTED, REJECTED, EXPIRED, CANCELLED all reject.
- Calls `sendInvitationEmail(invitation, site)` — same path used by `invite(...)`, so the row is updated identically whether this is the first send or the Nth.
- Returns the refreshed `SiteInvitationDto`.

### Controller endpoint

```
POST /api/v1/sites/{siteId}/invitations/{invitationId}/resend
```

`@PreAuthorize("isAuthenticated()")` at the method (no class-level mount on this controller; matches the existing per-method style). The service-side `ensureCanManageInvitations` is the actual permission gate.

Exception → status mapping (via existing `RestExceptionHandler`):

| Exception thrown | HTTP |
| --- | --- |
| `IllegalArgumentException` (non-PENDING) | 400 |
| `SecurityException` (non-manager/non-admin) | 403 |
| `ResourceNotFoundException` (unknown id or site mismatch) | 404 |
| Anonymous request | 401 (Spring Security gate) |

### `SiteInvitationDto` extension

The public record gains five fields appended at the end:

```java
public record SiteInvitationDto(
    UUID id, UUID siteId, String siteTitle, String inviteeEmail,
    String inviteeUsername, String invitedRole, String status, String message,
    String invitedBy, LocalDateTime expiresAt, LocalDateTime acceptedAt, LocalDateTime createdDate,
    // Send-status tracking (migration 092). All nullable except sendAttemptCount.
    LocalDateTime lastSendAttemptAt,
    String lastSendStatus,        // "SENT" | "FAILED" | null
    String lastSendError,         // null when status is SENT or null
    int sendAttemptCount,         // primitive int, default 0
    LocalDateTime lastSentAt
) {}
```

Nullability is part of the contract. The frontend (Package B) renders fallback placeholders for null values.

## Verification

Targeted Surefire run from the worktree's `ecm-core/`:

```
mvn -B -q -Dstyle.color=never -Dmaven.repo.local=.m2-cache/repository \
  -Dspring.profiles.active=test \
  -Dtest=SiteInvitationServiceTest,SiteInvitationControllerSecurityTest,SiteInvitationControllerTest,EmailNotificationServiceTest test
```

Result:

| Suite | Tests | Failures | Errors |
| --- | --- | --- | --- |
| `SiteInvitationServiceTest` | 8 | 0 | 0 |
| `SiteInvitationControllerSecurityTest` | 8 | 0 | 0 |
| `SiteInvitationControllerTest` | 5 | 0 | 0 |
| `EmailNotificationServiceTest` | 11 | 0 | 0 |

Total: 32 passed, 0 failed, 0 errors.

`git diff --check` clean before commit.

### Coverage notes

- `EmailNotificationServiceTest` (extended): existing 9 tests continue to assert the legacy `send(...)` swallow-and-warn behavior unchanged; 2 new tests directly exercise `sendSync` — one asserts `SendResult(true, null)` on a successful dispatch, another asserts `SendResult(false, "SMTP send failed: ...")` carrying the underlying exception message when `mailSender.send` throws `MailException`. The pre-check failure branches are exercised by the existing legacy-`send` tests because `send` now delegates to `sendSync`.
- `SiteInvitationServiceTest`: resend happy / failure / non-PENDING / cross-site / unknown id / non-manager paths; `invite()` populates send-tracking fields after a successful first send.
- `SiteInvitationControllerSecurityTest`: anonymous resend → 401; non-PENDING → 400; non-manager → 403; unknown id → 404; success → 200 with `lastSendStatus=SENT`, `sendAttemptCount=2`, `lastSentAt` populated, `lastSendError` absent.
- `SiteInvitationControllerTest`: existing fixture call sites updated for the 16-field DTO record.

## Files Changed

- `ecm-core/src/main/resources/db/changelog/changes/092-add-site-invitation-send-tracking.xml` (new)
- `ecm-core/src/main/resources/db/changelog/db.changelog-master.xml` (registered 092)
- `ecm-core/src/main/java/com/ecm/core/entity/SiteInvitation.java` (5 columns + LastSendStatus enum)
- `ecm-core/src/main/java/com/ecm/core/integration/email/notify/EmailNotificationService.java` (sendSync + SendResult; @Async send delegates)
- `ecm-core/src/main/java/com/ecm/core/service/SiteInvitationService.java` (sendInvitationEmail rewrite; resend method; DTO extension)
- `ecm-core/src/main/java/com/ecm/core/controller/SiteInvitationController.java` (POST /resend)
- `ecm-core/src/test/java/com/ecm/core/integration/email/notify/EmailNotificationServiceTest.java` (2 new sendSync return-shape tests)
- `ecm-core/src/test/java/com/ecm/core/service/SiteInvitationServiceTest.java` (8 tests)
- `ecm-core/src/test/java/com/ecm/core/controller/SiteInvitationControllerSecurityTest.java` (8 tests; 5 new resend cases)
- `ecm-core/src/test/java/com/ecm/core/controller/SiteInvitationControllerTest.java` (DTO call-site updates)

## Remaining Work

- No automatic retry worker. Deliberate v1 decision; Package C's runbook records the rationale and revisit triggers.
- No per-recipient delivery tracking (delivered / bounced / complained). Provider-specific webhooks are not wired in v1.
- No concurrent-resend protection. Two operators clicking resend simultaneously will serialize on the row's write lock; in practice acceptable, but if the failure set grows we may add a short cooldown.
- The synchronous send extends the @Transactional duration. If SMTP becomes a hot path or latency target, revisit by either splitting the send out of the transaction (with a follow-up update transaction) or restoring async dispatch with a separate audit log table.
