# Site Invitation Live-Send Closeout

Date: 2026-05-07

## Context

Site invitation infrastructure shipped in an earlier round: the `SiteInvitation` entity, the `SiteInvitationService`, the token-based accept/reject endpoints, the `site.invitation` template seed (migration 087), and the later template-link update (migration 089). The `EmailNotificationService` that dispatches the message was already in place.

This doc is a closeout, not a feature delivery. It captures the operator-side configuration, accept-URL semantics, failure observability, and the smoke flow needed to confirm a live invitation email reaches a real inbox. The doc does NOT propose any code change. Its purpose is to record (a) what an operator must set on the deployment to make invitation emails actually go out, (b) what log lines to grep for when something goes wrong, and (c) a deterministic happy-path smoke flow plus a deliberate-failure smoke flow.

For the prerequisite "is `spring.mail.*` configured at all?" check, run the SMTP test runbook ([`SMTP_TEST_OPERATOR_RUNBOOK_20260507.md`](./SMTP_TEST_OPERATOR_RUNBOOK_20260507.md)) first.

## Template

Confirmed by reading the source:

- **Template key:** `site.invitation`. Used by `SiteInvitationService.sendInvitationEmail` (the `emailService.send("site.invitation", ...)` call at `SiteInvitationService.java:297-302`).
- **Initial seed:** `ecm-core/src/main/resources/db/changelog/changes/087-seed-site-invitation-email-template.xml`. Inserts the `default` locale row with subject `Athena: You have been invited to ${siteTitle}` and a plain-text body.
- **Later update:** `ecm-core/src/main/resources/db/changelog/changes/089-update-site-invitation-email-template-link.xml`. Updates the body to embed the direct `${invitationUrl}` accept link in addition to the raw `${token}` fallback.
- **Resolution path:** `EmailNotificationService.resolveTemplate(...)` calls `EmailTemplateRepository.findByTemplateKeyAndLocaleInOrderByLocaleAsc(...)`. Locale fallback is computed by `EmailNotificationService.computeLocaleFallbacks(...)` — the preferred locale plus its language-only prefix plus the literal `default` locale, in that order. Since the `SiteInvitationService` passes `null` for the preferred locale (`emailService.send("site.invitation", invitation.getInviteeEmail(), null, variables)`), resolution falls through to the `default`-locale row seeded by migration 087.
- **Placeholders the template can reference:** `siteTitle`, `siteId`, `invitedBy`, `token`, `role`, `message`, `expiresAt`, `invitationUrl`. Read directly from `SiteInvitationService.sendInvitationEmail` lines 287-295 — the variables `Map` populated immediately before the `emailService.send(...)` call.

The seeded `default`-locale body uses all eight placeholders (see migration 089). Placeholder substitution happens via Spring's `PropertyPlaceholderHelper("${", "}", ":", true)` in `EmailNotificationService` — missing or null variables are substituted with the empty string by `SiteInvitationService` (it explicitly sets `message` to `""` when the invitation has no message, and other variables come straight from the `SiteInvitation` and `Site` rows so they cannot be missing).

## Accept URL

Confirmed by reading the source:

- **Path shape:** `${ecm.frontend.base-url}/invitations/accept?token=<URL-encoded token>`. Built by `SiteInvitationService.buildInvitationUrl` (lines 308-315). The base URL has trailing slashes stripped (`replaceAll("/+$", "")`) before concatenation.
- **Token encoding:** `URLEncoder.encode(token, StandardCharsets.UTF_8)`. The token itself is 32 random bytes hex-encoded by `SiteInvitationService.generateToken` (line 246-250), so URL encoding is a defensive measure rather than a hard requirement — hex characters are URL-safe — but the encoder call is what the code actually emits and what the operator should expect to see in any link the operator inspects.
- **Default `ecm.frontend.base-url`:** `http://localhost:3000`. From `@Value("${ecm.frontend.base-url:http://localhost:3000}")` on `SiteInvitationService.frontendBaseUrl` (line 49). **Production deployments MUST set `ECM_FRONTEND_BASE_URL`** to the public hostname the recipient's browser will actually reach. If the recipient is outside the deployment's network and the env var is left at `http://localhost:3000`, the link in the invitation email will be unusable.
- **Frontend route:** `/invitations/accept?token=<...>`. The frontend page consumes the `token` query parameter and posts it to `POST /api/v1/invitations/accept` with the body `{"token": "..."}`. Endpoint is wired on `SiteInvitationController.accept` (lines 62-67); per `@PreAuthorize("isAuthenticated()")`, the recipient must be signed in to Athena before they can accept.

## Required env (live send)

This is the full set of env vars an operator must set for an invitation email to actually be dispatched and for its accept link to be reachable. The "test-only" column distinguishes which vars the Test SMTP runbook also requires, versus which are unique to the live invitation flow.

| Env var | Required for live send | Required by Test SMTP runbook | Notes |
|---|---|---|---|
| `ECM_EMAIL_ENABLED` | yes (`true`) | no (Test SMTP bypasses it) | `EmailNotificationService.send` short-circuits with `log.debug("send: ecm.email.enabled=false; skipping templateKey={}", ...)` when this is `false`. Site invitations rely on this flag. |
| `ECM_EMAIL_FROM_ADDRESS` | yes | yes | Sender display address. Used by `MimeMessageHelper.setFrom`. |
| `ECM_FRONTEND_BASE_URL` | yes | n/a (Test SMTP does not include a link) | Drives the accept-URL host in the body. Default `http://localhost:3000` is unusable for any external recipient. |
| `SPRING_MAIL_HOST` | yes | yes | SMTP server. |
| `SPRING_MAIL_PORT` | yes | yes | Usually 465 (SSL) or 587 (STARTTLS). |
| `SPRING_MAIL_USERNAME` | yes | yes | Full email address. |
| `SPRING_MAIL_PASSWORD` | yes | yes | Provider client authorization code where applicable. |
| `SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH` | yes (`true`) | yes | Required by all five Chinese enterprise mailbox presets. |
| `SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_ENABLE` | for SSL/465 | for SSL/465 | `true` when using port 465. |
| `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE` | for STARTTLS/587 | for STARTTLS/587 | `true` when using port 587. |

Provider-side prerequisites (enabling SMTP at the admin console, generating the client authorization code, and confirming the host/port/security match the canonical row) are documented in [`MAIL_PROVIDER_SETUP_ALIYUN_TENCENT_263_20260507.md`](./MAIL_PROVIDER_SETUP_ALIYUN_TENCENT_263_20260507.md).

## Failure observability

The invitation send path is fire-and-forget: the API call that creates the invitation row returns success regardless of whether the email actually gets dispatched. Confirmed by reading the source:

- `EmailNotificationService.send(...)` is annotated `@Async` (`EmailNotificationService.java:42`). The call returns to `SiteInvitationService.sendInvitationEmail` immediately and the actual SMTP exchange runs on the async executor.
- `EmailNotificationService.send` swallows `MailException` with `log.warn("send: mail dispatch failed templateKey={} cause={}", templateKey, ex.getMessage())` (lines 98-103). It also catches generic `Exception` with `log.warn("send: unexpected failure templateKey={} cause={}", ...)` (lines 104-110).
- `SiteInvitationService.sendInvitationEmail` wraps the entire `emailService.send(...)` invocation in `try { ... } catch (Exception ex) { log.warn("Failed to send invitation email to {}: {}", invitation.getInviteeEmail(), ex.getMessage()); }` (lines 286-305). Because the underlying `send` call is async, this catch primarily covers synchronous setup failures (e.g. building the variables map) — async dispatch failures inside `EmailNotificationService.send` are reported via the `send: mail dispatch failed` log line above, not via this catch.

The practical consequence: a failed invitation email does NOT fail the `POST /api/v1/sites/{siteId}/invitations` API call. The `SiteInvitation` row persists in `PENDING` status and the invitee gets nothing. The only way to detect this from outside the application logs is to notice that the invitee never accepts — there is no per-recipient delivery-status field on the `SiteInvitation` row.

**Operator grep targets in application logs:**

- Successful dispatch: `send: dispatched templateKey=site.invitation`. From `EmailNotificationService.send` line 92-97 (`log.info("send: dispatched templateKey={} subjectLen={} bodyLen={}", ...)`).
- Async dispatch failure (most common when SMTP itself is misconfigured or the provider rejects the credentials): `send: mail dispatch failed templateKey=site.invitation`. From `EmailNotificationService.send` line 99-103.
- Unexpected non-`MailException` failure: `send: unexpected failure templateKey=site.invitation`. From `EmailNotificationService.send` line 105-110.
- Synchronous setup failure inside `SiteInvitationService`: `Failed to send invitation email to <addr>: <message>`. From `SiteInvitationService.sendInvitationEmail` line 304.
- `EmailNotificationService.send` short-circuit reasons (these surface as `WARN` log lines without dispatching): `send: missing recipient for templateKey=site.invitation`, `send: ecm.email.from-address not configured; skipping templateKey=site.invitation`, `send: JavaMailSender not configured; skipping templateKey=site.invitation`, `send: template not found key=site.invitation locale=...`. The `ecm.email.enabled=false` short-circuit logs at `DEBUG`, not `WARN` — bump the `EmailNotificationService` logger to `DEBUG` if the operator suspects the flag is the cause.

When triaging an "invitation never arrived" report, grep the application log first for `templateKey=site.invitation` and read whichever line came back. If no line came back at all, the API call did not reach `SiteInvitationService.sendInvitationEmail` — check the `SiteInvitation` row exists in `PENDING` and that the API response was `201 Created`.

## Smoke flow

A deterministic verification path the operator runs after wiring the env vars above. The happy-path steps validate "live invitation works end-to-end against this deployment"; the failure step validates "the operator's logs surface a misconfiguration cleanly without breaking the API".

1. **SMTP prerequisite.** Run the test sequence from [`SMTP_TEST_OPERATOR_RUNBOOK_20260507.md`](./SMTP_TEST_OPERATOR_RUNBOOK_20260507.md) first. Do NOT proceed unless the Test SMTP control returned the green `Sent! SMTP <host>:<port> from <fromAddress>` Alert and the test message arrived in the operator's own inbox. If the SMTP test fails, the invitation send will also fail; debug SMTP first.
2. **Send a real invitation.** As an admin (or as a manager of a target site), use the Sites admin UI to invite an email address you control. Equivalent backend call: `POST /api/v1/sites/{siteId}/invitations` with body `{"inviteeEmail": "<your-address>", "invitedRole": "CONSUMER", "message": "smoke test"}`. The endpoint is on `SiteInvitationController.invite` (lines 39-47) and returns `201 Created` with the `SiteInvitationDto`.
3. **Inbox check.** Within ~5 seconds, check the recipient inbox. The subject is whatever the seeded template emits — at the time of writing (migration 087, unchanged by 089) that is `Athena: You have been invited to <siteTitle>`. The body is plain text and contains both the direct `Accept invitation: <invitationUrl>` link and the raw `Invitation token: <token>` fallback (per migration 089).
4. **Accept the invitation.** Click the `Accept invitation: <invitationUrl>` link. Confirm the URL routes to the production frontend's `/invitations/accept?token=<...>` page (i.e. `ECM_FRONTEND_BASE_URL` resolves to the public hostname, not `localhost:3000`). Sign in if prompted; the accept endpoint requires authentication.
5. **Confirm acceptance landed.** Complete the accept flow. Verify in the admin invitation list (`GET /api/v1/sites/{siteId}/invitations`, on `SiteInvitationController.listInvitations` lines 32-37) that the row's `status` is `ACCEPTED` and `acceptedAt` is set. The recipient should also appear in the site's member list.
6. **Failure smoke.** Rotate `SPRING_MAIL_PASSWORD` to a deliberately invalid value, restart the application, and send another invitation to the same recipient. Expect:
   - The API still returns `201 Created`. The `SiteInvitation` row still persists in `PENDING`.
   - The recipient inbox does NOT receive a new message.
   - The application log emits `send: mail dispatch failed templateKey=site.invitation cause=...` (the `cause=` field will typically be a `MailAuthenticationException` message). Confirm by grepping the log.
   - Restore `SPRING_MAIL_PASSWORD` to the valid value and restart afterwards. (You may also wish to cancel the failed `PENDING` invitation via `DELETE /api/v1/sites/{siteId}/invitations/{invitationId}` so it does not linger.)

If all six steps behave as described, the live invitation send path is confirmed end-to-end on this deployment.

## What this closeout does NOT change

Explicit non-goals of this round. The point of the closeout was to confirm and document the existing live-send path, not to rebuild it. Out of scope:

- **No new template fields.** The `site.invitation` template still uses the same eight placeholders (`siteTitle`, `siteId`, `invitedBy`, `token`, `role`, `message`, `expiresAt`, `invitationUrl`) supplied by `SiteInvitationService.sendInvitationEmail`. Adding a new placeholder would require a service-side variable population change plus a template-body migration, both of which are deferred.
- **No new endpoint.** The existing `POST /api/v1/sites/{siteId}/invitations` (create), `DELETE /api/v1/sites/{siteId}/invitations/{invitationId}` (cancel), `POST /api/v1/invitations/accept`, and `POST /api/v1/invitations/reject` already cover the workflow. No "resend invitation" endpoint is added.
- **No retry / dead-letter queue for failed sends.** A failed `EmailNotificationService.send` is logged at `WARN` and never retried. Adding retry / DLQ semantics would require a persistent outbox table and a worker; both are deferred.
- **No per-recipient delivery tracking.** The `SiteInvitation` row exposes `status` (`PENDING` / `ACCEPTED` / `REJECTED` / `CANCELLED` / `EXPIRED`) and `acceptedAt`, but does not record send-time delivery state, bounces, or open events. Adding such tracking would require either inspecting SMTP server bounce reports or integrating a transactional-mail provider — both deferred.
- **No HTML body for the invitation template.** Migration 087 seeds with `html_body=false` and migration 089 leaves it unchanged. The current `default`-locale row remains plain text; an HTML template could be added in a later round (either as a new locale row or as an `html_body=true` replacement) without service-side changes.

These items remain candidates for a future round if and when the operator workflow demands them.
