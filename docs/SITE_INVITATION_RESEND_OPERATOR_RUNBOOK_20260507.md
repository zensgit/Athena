# Site Invitation Resend - Operator Runbook

Date: 2026-05-07

## Context

Site invitations now record per-row send status. When the SMTP path fails — transient provider issue, bad address, expired credentials — the row stays `PENDING` but `lastSendStatus` flips to `FAILED` with a `lastSendError` message captured from the underlying `MailException` / generic `Exception`. Operators retry manually via the `Resend email` action on the SiteInvitationsPage admin UI; v1 does NOT auto-retry. This runbook walks through reading the status, deciding whether to resend, the error-to-fix mapping, the smoke flow, and explicitly records the v1 decision against shipping an auto-retry worker.

For the underlying live-send path (template, accept URL, env vars, log lines), see [`SITE_INVITATION_LIVE_SEND_CLOSEOUT_20260507.md`](./SITE_INVITATION_LIVE_SEND_CLOSEOUT_20260507.md). For the prerequisite "is `spring.mail.*` configured at all?" check, run the SMTP test runbook ([`SMTP_TEST_OPERATOR_RUNBOOK_20260507.md`](./SMTP_TEST_OPERATOR_RUNBOOK_20260507.md)) first.

## Reading the send status

The admin Invitations list shows a status chip per row driven by `lastSendStatus` plus the surrounding row state. Read it together with `status` and `sendAttemptCount` to decide whether anything is needed:

| `status` | `lastSendStatus` | `sendAttemptCount` | Interpretation | Recommended action |
|---|---|---|---|---|
| PENDING | SENT | 1+ | Last send succeeded; recipient should have it. Awaiting accept/reject. | None unless recipient reports they didn't receive it. |
| PENDING | FAILED | 1+ | Last send failed; recipient has nothing. | Read `lastSendError`, fix root cause if needed, then `Resend email`. |
| PENDING | null | 0 | Invitation row exists but the send was never attempted (rare — would only happen on a deployment where `EmailNotificationService` was unavailable at invite-time). | `Resend email` once the email service is configured. |
| ACCEPTED | SENT | 1+ | Recipient accepted. | None. |
| ACCEPTED | FAILED | 1+ | Recipient accepted via a token they got from another channel (forwarded link, manual share). The original email failed but the accept landed anyway. | None — informational. |
| EXPIRED / REJECTED / CANCELLED | * | * | Invitation is closed; resend is rejected with 400. Create a new invitation if needed. | Create a new invitation; do not resend. |

`lastSendAttemptAt` is the timestamp of the most recent attempt regardless of outcome; `lastSentAt` only advances on success. The two diverging is itself a signal — if `lastSendAttemptAt > lastSentAt`, at least one failure has occurred since the last successful send.

### What `sendAttemptCount` tells you

Because the count only advances on operator-initiated sends (the initial `invite` plus each `resend` click), it carries an audit signal that a pure log scrape cannot:

- `sendAttemptCount = 1` and `lastSendStatus = SENT` — the original invite went out cleanly, no operator has had to intervene.
- `sendAttemptCount = 1` and `lastSendStatus = FAILED` — the original invite failed and nobody has retried yet. This is the row that most likely needs operator attention right now.
- `sendAttemptCount >= 2` — at least one resend has been attempted. Compare `lastSendStatus` and `lastSendError` to see whether the most recent retry worked. A high count with `lastSendStatus = FAILED` indicates persistent root cause that resends will not fix on their own (config drift, dead recipient, blocked sender) — stop clicking and read the error.

There is no separate "resend log"; the row itself is the audit record. If you need historical attempt-by-attempt detail beyond what the row carries, fall back to grepping the application log for `templateKey=site.invitation` filtered by recipient.

## Resend flow

1. Sign in as admin OR site MANAGER. (The endpoint enforces the same gate as `invite` / `cancel` via `ensureCanManageInvitations`.)
2. Navigate to the site's Invitations page.
3. Locate the row(s) showing `FAILED` (red chip) or `Not yet sent` (grey chip) on `PENDING` invitations.
4. Hover the chip / read the failure caption to see `lastSendError`. Common values are listed in the next section.
5. If the error is fixable in operator config (SMTP credentials, env var), fix it first and restart Athena before resending. Resending against the same misconfigured config will fail the same way and burn an attempt count.
6. Click `Resend email` on the row. Confirm the dialog.
7. The row is replaced from the response. Expect `lastSendStatus=SENT` and `lastSentAt` updated. `sendAttemptCount` increments by 1.
8. If `lastSendStatus=FAILED` after the resend, read the new `lastSendError` and re-iterate.

The endpoint backing the action is `POST /api/v1/sites/{siteId}/invitations/{invitationId}/resend`. It accepts `PENDING` rows only — `ACCEPTED` / `EXPIRED` / `REJECTED` / `CANCELLED` rows are rejected with `IllegalArgumentException` → HTTP 400. Create a new invitation in that case.

### Bulk failed resend flow

The Invitations page also exposes `Resend failed (N)` beside the send-result
filter chips. This is a frontend-only bulk action that reuses the same per-row
`/resend` endpoint; there is no separate bulk backend endpoint.

Eligibility is intentionally strict: only rows with `status=PENDING` and
`lastSendStatus=FAILED` are included. Closed rows whose historical send status
is `FAILED` are skipped because the backend rejects resending non-`PENDING`
invitations.

Use this action after reading the failure pattern and fixing any shared root
cause (for example SMTP credentials). The page asks for browser confirmation,
then attempts each eligible resend and updates rows from the returned DTOs.
The summary toast reports how many returned `SENT`, how many remained or became
`FAILED`, and how many returned a non-terminal send status.

### Permissions and audit

- The endpoint enforces the same caller gate as `invite` and `cancel`: admin OR site MANAGER. CONTRIBUTOR / CONSUMER members of the same site cannot resend even invitations they themselves received notice of. Calls from non-admin / non-MANAGER callers respond with HTTP 403 (`AccessDeniedException`).
- A resend produces a single `send: dispatched templateKey=site.invitation` (or failure) log line and increments `sendAttemptCount` by 1. There is no separate "resend.requested" event — the existence of `sendAttemptCount > 1` plus the timestamp on `lastSendAttemptAt` is the audit trail.
- If the site has been soft-deleted between invite and resend, `loadSite(siteId)` returns 404 (`ResourceNotFoundException` via `findBySiteIdIgnoreCaseAndDeletedFalse`). The invitation cannot be resent against a deleted site; the row remains in whatever status it was last in. Restore the site or create a new invitation under a replacement site.

## Common `lastSendError` values

The `lastSendError` string is the same message text the application log emits in the `cause=...` field. Map the prefix to a fix:

| Error message (prefix) | Root cause | Fix |
|---|---|---|
| `ecm.email.enabled is false` | Notifications globally disabled. | `ECM_EMAIL_ENABLED=true`, restart. (`Test SMTP` admin control bypasses this flag for SMTP-only verification; site invitations do NOT.) |
| `ecm.email.from-address is not configured` | Sender address unset. | `ECM_EMAIL_FROM_ADDRESS=<sender>`, restart. |
| `JavaMailSender is not configured` | `spring.mail.host` unset. | Configure `SPRING_MAIL_HOST` / `SPRING_MAIL_PORT` / etc. per the SMTP runbook. Restart. |
| `template not found: site.invitation ...` | Liquibase migration 087/089 didn't run, or template was manually deleted. | Re-run migrations or restore the template from migration 087. |
| `SMTP send failed: 535 ...` | Authentication failed. | Wrong password OR provider requires a client authorization code (Tencent, 263). See the mail provider setup doc. |
| `SMTP send failed: SSLHandshakeException ...` | TLS misconfig (port/security mismatch). | 465=SSL needs `mail.smtp.ssl.enable=true`; 587=STARTTLS needs `mail.smtp.starttls.enable=true`. |
| `SMTP send failed: Could not connect to host ...` | Wrong host or firewall. | Verify against the per-provider table in [`MAIL_PROVIDER_SETUP_ALIYUN_TENCENT_263_20260507.md`](./MAIL_PROVIDER_SETUP_ALIYUN_TENCENT_263_20260507.md). |
| `unexpected send failure: <ExceptionClass> ...` | Anything else (e.g. JavaMail bug, OOM). | Read the application log around the timestamp; the underlying stack trace is in the log even though the response truncates to the message. |

If a value does not match any of these prefixes, grep the application log for `templateKey=site.invitation` around `lastSendAttemptAt` — the underlying `EmailNotificationService` log lines (`send: mail dispatch failed`, `send: unexpected failure`) carry the same `cause=` text plus a stack trace for the `unexpected failure` branch.

## Why no auto-retry worker in v1

v1 deliberately does not ship a background worker that re-attempts failed sends. The reasons are factual, not aspirational:

- **Most failure modes are persistent, not transient.** Bad recipient address, missing client authorization code, mis-set `spring.mail.*` env. Auto-retry against persistent failures wastes compute and amplifies provider rate-limit risk. Manual resend forces an operator to read the error first, which is a productive constraint.
- **Manual resend gives audit trail.** `sendAttemptCount` increments only when an operator deliberately retries. With auto-retry, the count would conflate genuine operator action with background polling, and operators would lose the ability to use `sendAttemptCount` as a "how many times has someone tried to fix this" signal.
- **Auto-retry needs a DLQ + scheduling worker + backoff state.** Adding all three for a small failure set (typical mailbox misconfig at deploy time) is over-engineering for v1. The infrastructure cost would exceed the savings.
- **Two adjacent flows already work this way.** Mail Automation per-account `Test connection` is operator-triggered; OAuth `Refresh Now` is operator-triggered. Site invitation resend matches the established Athena pattern.

### Revisit triggers

These conditions would cause us to re-open the auto-retry decision:

- When > 5% of invitation sends fail in a sustained window AND root-cause analysis shows transient provider issues, NOT config drift. (Config drift is fixed by config; auto-retry would mask it.)
- When the operator base grows beyond what manual resend can keep up with — suggest > 50 failed invitations / week as the threshold.
- When a customer SLA explicitly requires automatic retry behavior.

Until one of these triggers fires, manual resend remains the supported retry path.

## Smoke flow

After deploying the resend layer, run this checklist to confirm the path is working end-to-end:

1. Run `Test SMTP` (the admin control shipped earlier in the SMTP round) to confirm the application-level SMTP path is healthy.
2. As an admin, create a site invitation for an email address you control (admin UI or `POST /api/v1/sites/{siteId}/invitations`).
3. Confirm `lastSendStatus=SENT` and `lastSentAt` is populated immediately (since `sendInvitationEmail` is now sync).
4. Trigger a deliberate failure: rotate `SPRING_MAIL_PASSWORD` to an invalid value, restart, then click `Resend email` on a `PENDING` invitation. Expect `lastSendStatus=FAILED` and `lastSendError` populated with `SMTP send failed: 535 ...`. The row remains `PENDING`.
5. Restore the password, restart, click `Resend email` again. Expect `lastSendStatus=SENT`, `lastSendError=null`, `lastSentAt` updated, `sendAttemptCount` increments.
6. Confirm via the admin UI that the failed and successful attempts both contributed to `sendAttemptCount` — the row should show a count of at least 3 (initial invite + failed resend + successful resend).

If all six steps behave as described, the resend layer is confirmed end-to-end on this deployment.

## Out of scope

Items deliberately not in v1 of the resend layer (matches the closeout doc's "What this closeout does NOT change" section):

- **No auto-retry worker** (see §5 "Why no auto-retry worker in v1" above for rationale and revisit triggers).
- **No per-recipient delivery tracking** (delivered / bounced / complained — provider-specific webhooks not wired). `lastSendStatus` records the dispatch outcome only; what happens after the SMTP server accepts the message is invisible to Athena.
- **No dedicated bulk backend endpoint.** The admin UI bulk action fans out to the existing per-row `/resend` endpoint for eligible failed `PENDING` rows.
- **No SMS or alternate-channel fallback.** If email fails, there is no automatic SMS / push / in-app message; the operator must use a side channel.
- **No template versioning or A/B testing.** The `site.invitation` template is single-version; changes ship via a new Liquibase migration.

These remain candidates for a future round if and when the operator workflow demands them.
