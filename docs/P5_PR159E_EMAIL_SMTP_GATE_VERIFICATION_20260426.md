# P5 PR-159e — EmailNotificationService SMTP integration gate (design + verification)

## Date
2026-04-26

## Status
Implementation complete. Closes the PR-159 email notification lane.
All 5 sub-slices committed and pushed to `origin/main`.

## Lane summary

| PR | Commit | Role |
|----|--------|------|
| PR-159 | `35c99ca` | Email backend foundation (EmailTemplate entity, service, migration 084) |
| PR-159a | `59c94bf` | ObjectProvider<JavaMailSender> — startup-safe optional sender |
| PR-159b | `714a18b` | NotificationChannel abstraction (InboxChannel, EmailChannel, NotificationDispatcher) |
| PR-159c | `ec5a16d` | notifyByEmail preference keys + resolveDeliveryChannels() |
| PR-159d | `e1c36b7` | Wire dispatcher into delivery event flow |
| PR-159e | `122b52c` | Embedded Greenmail SMTP integration gate |

## Scope of PR-159e

Add a live-SMTP integration test that exercises `EmailNotificationService.send()`
through a real SMTP session using an in-process Greenmail server. This gate catches
MIME encoding, header, and content-type issues that mocked-sender tests cannot surface.

## Files changed in PR-159e

| File | Change |
|------|--------|
| `ecm-core/pom.xml` | +`com.icegreen:greenmail:2.1.2` (test scope) |
| `ecm-core/src/test/java/com/ecm/core/integration/email/notify/EmailNotificationServiceSmtpTest.java` | new — 3 SMTP integration tests |

## Design

### Greenmail setup

Greenmail runs as an in-process SMTP server on `ServerSetupTest.SMTP` (port 3025).
No Docker, no external process — the server starts in `@BeforeEach` and stops in
`@AfterEach`. Authentication is disabled via `GreenMailConfiguration.aConfig().withDisabledAuthentication()`.

`JavaMailSenderImpl` is constructed pointing at `127.0.0.1:3025` with auth=false,
STARTTLS=false (plain SMTP, appropriate for localhost tests). This real sender is
returned by the mocked `ObjectProvider<JavaMailSender>.getIfAvailable()`.

### Spring @Async behavior

`EmailNotificationService.send()` is annotated `@Async`. Without a Spring
application context, the `@Async` AOP proxy is not activated — `send()` executes
synchronously on the test thread. `greenMail.waitForIncomingEmail(3000, 1)` is
therefore usually satisfied in microseconds; the 3-second timeout is a safety net.

### Template resolution

`EmailTemplateRepository` is mocked to return a specific `EmailTemplate` per test.
The live-SMTP assertion does not depend on a database — only the SMTP transport
layer is real.

## Tests

| Test | Verifies |
|------|---------|
| `delivers_renderedPlainTextEmail_viaSmtp` | Subject rendered with `${presetName}`; body rendered; To/From headers set; message received within timeout |
| `delivers_htmlEmail_viaSmtp` | Content-type is `text/html`; subject rendered for failure template |
| `delivers_withLocaleFallback_viaSmtp` | zh-TW locale request → zh template selected; Chinese subject/body transmitted correctly |

All three tests use `@ExtendWith(MockitoExtension.class)`, no Spring context,
and assert on the received `MimeMessage` objects from `greenMail.getReceivedMessages()`.

## Expected CI behavior

The `Backend Verify` job runs `./mvnw test`. Maven will resolve `com.icegreen:greenmail:2.1.2`
from Maven Central and run the 3 new SMTP tests alongside the 9 existing
`EmailNotificationServiceTest` unit tests and the 6 `NotificationDispatcherTest` unit tests.

| Test class | Count | Gate |
|------------|------:|------|
| `EmailNotificationServiceTest` | 9 | Backend Verify |
| `EmailNotificationServiceSmtpTest` | 3 | Backend Verify |
| `NotificationDispatcherTest` | 6 | Backend Verify |
| `RmReportPresetDeliveryServiceChannelResolutionTest` | 6 | Backend Verify |
| `RmReportPresetDeliveryServiceTest` (incl. updated) | ≥15 | Backend Verify |

All run without Docker — Greenmail is embedded, no container required.

## Full PR-159 delivery architecture (as shipped)

```
Scheduled delivery completion
  → publishSuccessful/FailedScheduledDeliveryNotification()
      → resolveDeliveryChannels(recipient, isSuccess)
          reads PREF_NOTIFY_ON_SUCCESS/FAILURE       → inbox gate (default ON)
          reads PREF_NOTIFY_BY_EMAIL_ON_SUCCESS/FAILURE → email gate (default OFF, opt-in)
          returns Set<String> channel IDs
      → activityService.createNotificationActivity()   [REQUIRES_NEW; audit record committed]
      → userRepository.findByUsername(recipient)        [email address lookup]
      → NotificationDispatcher.dispatch(payload, channels)
            InboxChannel  → NotificationInboxService.createDirectNotification()
            EmailChannel  → EmailNotificationService.send()  [@Async, fire-and-forget]
                               → EmailTemplateRepository (template lookup + locale fallback)
                               → PropertyPlaceholderHelper (${var} substitution)
                               → JavaMailSender.send(MimeMessage)  [real SMTP in prod]
```

## Security

- `ecm.email.enabled` defaults to `false` — double guard against accidental sends
- Email opt-in prefs default to `false` — no emails without explicit user action
- `fromAddress` validated non-blank before any SMTP session is opened
- `ObjectProvider<JavaMailSender>` — null when `spring.mail.host` not configured; service bails silently
- Email body built with `PropertyPlaceholderHelper` — no template injection risk (only `${key}` substitution, not eval)

## Non-goals (deferred to future PRs)

- Frontend preference toggle UI (PR-159c-frontend)
- Seed data: example `EmailTemplate` rows for the default delivery events
- Per-user locale preference (currently hardcoded `"default"`)
- HTML email styling / CSS inlining
