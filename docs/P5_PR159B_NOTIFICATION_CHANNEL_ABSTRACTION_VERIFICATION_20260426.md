# P5 PR-159b — NotificationChannel abstraction (design + verification)

## Date
2026-04-26

## Status
Implementation. Backend-only. Builds on `35c99ca`/`59c94bf` (PR-159
email backend foundation, CI-verified green on Backend Verify).

## Trigger

Backend Verify on `59c94bf` (PR-159 + ObjectProvider refinement)
completed green at 06:54:07 → 06:55:xx, exercising:
- new `spring-boot-starter-mail` dependency resolution
- migration `084-email-notification-foundation` apply on a fresh
  schema
- 9 unit tests in `EmailNotificationServiceTest` including the new
  `skipsSilently_whenMailSenderUnavailable`

The `EmailNotificationService.send(...)` API contract is now CI-stable.
PR-159b can build on top with confidence.

## Scope

Add a thin abstraction on top of the two existing dispatch paths:

| Channel | Wraps |
|---------|-------|
| `inbox` (`InboxChannel`) | `NotificationInboxService.createDirectNotification(userId, activity)` |
| `email` (`EmailChannel`) | `EmailNotificationService.send(templateKey, toAddress, locale, vars)` |

Plus a `NotificationDispatcher` that takes a `NotificationPayload`
and a `Collection<String>` of channel IDs and fans out — failures
in one channel do not abort dispatch to siblings.

**No callers in this slice.** `RmReportPresetDeliveryService`
continues to call `activityService.postDirectNotificationActivity(...)`
directly. Wiring the dispatcher into the delivery event flow is
PR-159d.

## Why this slice exists

After PR-159, both channels exist as concrete services
(`NotificationInboxService` and `EmailNotificationService`), but
they have different signatures. A caller that wants to dispatch to
both has to:

```java
notificationInboxService.createDirectNotification(userId, activity);
emailNotificationService.send(templateKey, toEmail, locale, vars);
```

After PR-159b, the same caller writes:

```java
NotificationPayload payload = NotificationPayload.builder()...build();
notificationDispatcher.dispatch(payload, Set.of("inbox", "email"));
```

The shape is independent of which channel the caller wants
(per-user preference can map a user to `Set.of("inbox")` or
`Set.of("inbox","email")` or `Set.of("email")` — that's PR-159c's
scope). Adding future channels (SMS, webhook, etc.) becomes a
single-class addition without touching every caller.

## Files

### New files (no modifications to existing code)

| File | Type | Lines |
|------|------|------:|
| `ecm-core/src/main/java/com/ecm/core/integration/email/notify/NotificationPayload.java` | DTO | 26 |
| `ecm-core/src/main/java/com/ecm/core/integration/email/notify/NotificationChannel.java` | interface | 11 |
| `ecm-core/src/main/java/com/ecm/core/integration/email/notify/InboxChannel.java` | impl | 47 |
| `ecm-core/src/main/java/com/ecm/core/integration/email/notify/EmailChannel.java` | impl | 38 |
| `ecm-core/src/main/java/com/ecm/core/integration/email/notify/NotificationDispatcher.java` | service | 49 |
| `ecm-core/src/test/java/com/ecm/core/integration/email/notify/NotificationDispatcherTest.java` | unit test | 165 |

Total ~336 lines, all in the `integration.email.notify` package
that PR-159 introduced. **Zero existing-code changes** — this
slice is pure additions.

## Architecture

### `NotificationPayload` — Lombok `@Value @Builder`

Carries the metadata both channels may need:

| Field | Type | Purpose |
|-------|------|---------|
| `type` | `String` | activity-type / template-key |
| `recipientUserId` | `String` | for inbox routing |
| `recipientEmail` | `String` | for email |
| `preferredLocale` | `String` | for email template fallback chain |
| `activity` | `Activity` | for inbox channel (already-saved JPA entity) |
| `templateVars` | `Map<String,Object>` | for email template substitution |

Immutable. `templateVars` defaults to empty map via `@Builder.Default`.
Fields not used by a channel are simply ignored by that channel —
e.g., `EmailChannel` does not look at `activity`, `InboxChannel` does
not look at `recipientEmail`.

### `NotificationChannel` interface

```java
public interface NotificationChannel {
    String INBOX = "inbox";
    String EMAIL = "email";

    String getId();
    void dispatch(NotificationPayload payload);
}
```

The two `INBOX`/`EMAIL` String constants give callers stable
identifiers without leaking the concrete class names.

### `InboxChannel`

Reads `recipientUserId` and `activity` from the payload, calls
`NotificationInboxService.createDirectNotification(userId, activity)`.
Bails (warn log) on missing recipient or missing activity. Wraps the
service call in `try/catch (Exception)` and logs at WARN — channel
exceptions never bubble to the dispatcher.

### `EmailChannel`

Reads `recipientEmail`, `type`, `preferredLocale`, `templateVars`
from the payload and delegates to
`EmailNotificationService.send(...)`. Bails (debug log) when
`recipientEmail` is blank — this is the common "user opted out of
email" case, not an error.

`EmailNotificationService` is itself defensive (bails on
`emailEnabled=false`, blank addresses, missing template, missing
`JavaMailSender` bean) — `EmailChannel` does not duplicate those
checks.

### `NotificationDispatcher`

Constructor-injected `List<NotificationChannel>`; Spring autowires
all channel beans. The dispatcher builds an immutable
`Map<String, NotificationChannel>` keyed by channel ID at startup.

`dispatch(payload, channelIds)`:

1. Returns silently on null payload or empty channel set
2. For each channel ID:
   - Look up the channel; warn-log if unknown, continue
   - Call `channel.dispatch(payload)` inside a `try/catch (Exception)`
   - A throwing channel does not abort the remaining iteration

Order is deterministic because we use a `LinkedHashMap` initialised
in the order Spring autowires the channels (typically alphabetical
by class name, but we don't rely on it).

## Tests

`NotificationDispatcherTest` — 6 unit tests with `@ExtendWith(MockitoExtension.class)`,
no Spring context:

| Test | Subject |
|------|---------|
| `routesToRequestedChannels` | Verifies inbox + email both receive their delegated calls when both IDs are in the channel set |
| `skipsUnknownChannel` | An ID with no matching bean is logged-and-skipped; siblings still dispatch |
| `singleChannelFailureDoesNotAbortOthers` | Inbox throws → email still dispatches; no exception bubbles |
| `emptyChannelSetIsNoOp` | Empty channel set → neither service is called |
| `emailChannel_skipsBlankEmail` | `recipientEmail=null` → `EmailNotificationService.send` is never called |
| `inboxChannel_skipsMissingActivity` | `activity=null` → `NotificationInboxService.createDirectNotification` is never called |

All tests use real channel implementations + mocked underlying
services. This is integration-style testing of the abstraction,
not the underlying services (those have their own tests).

## What this enables

- **PR-159c (next slice)**: introduces preference keys
  `org.athena.rm.reportPreset.delivery.notifyByEmailOnSuccess` and
  `notifyByEmailOnFailure`. The wiring layer reads these and builds
  `Set.of("inbox")` or `Set.of("inbox","email")` accordingly.
- **PR-159d**: `RmReportPresetDeliveryService.notifySuccess(...)` /
  `notifyFailure(...)` switches from direct `activityService.postDirectNotificationActivity(...)`
  to:
  1. `activityService.saveActivity(...)` (audit only)
  2. `notificationDispatcher.dispatch(payload, channels)` (routes to
     inbox + optionally email)
- **Future channels**: a `SmsChannel` would just implement `NotificationChannel`
  with `getId() = "sms"` and an SMS service delegate — no other code
  changes.

## Verification

### Local
- `tsc --noEmit` — N/A, no frontend change
- `./mvnw clean test` — N/A locally (Docker delegate; same constraint
  as the rest of the project)
- Static check — code follows existing patterns: `@Service`,
  `@Component`, `@RequiredArgsConstructor`, constructor-list
  injection of multi-bean interface implementations

### Expected CI signal on next push

| Job | Expected |
|-----|----------|
| Backend Verify | Runs the new `NotificationDispatcherTest` (6 tests). Expects all pass. |
| Frontend Build & Test | Unchanged |
| Phase C Security | Unchanged |
| Acceptance Smoke | Unchanged |
| Frontend E2E Core Gate | Unchanged |
| Phase 5 Mocked Regression Gate | Unchanged (still green from `42fb994`) |

The Backend Verify gate is the meaningful signal. The new beans
register but no caller invokes them yet, so any defect would surface
as a Spring context startup failure (e.g., circular dependency) or
a unit-test assertion failure.

## Security / production-impact

- **No production behavior change.** The dispatcher and channels
  exist as Spring beans but are not invoked by any caller. The
  existing inbox-only path through `activityService.postDirectNotificationActivity(...)`
  is unchanged.
- `EmailNotificationService` defaults to `ecm.email.enabled=false`,
  so even if a caller invoked the dispatcher with `email` in the
  channel set, no email would actually send until SMTP is configured.
- No new database migration in this slice.

## Sequencing

| PR | Role | Status |
|----|------|--------|
| PR-159 | Email backend foundation | ✅ green on `35c99ca` (Backend Verify) |
| PR-159a | `ObjectProvider<JavaMailSender>` refinement | ✅ green on `59c94bf` (Backend Verify) |
| **PR-159b** | **NotificationChannel abstraction** | **✅ this turn — pending push + Backend Verify** |
| PR-159c (next) | `notifyByEmail*` preferences + UI toggle | After PR-159b verdict |
| PR-159d | Wire delivery events through dispatcher | After PR-159c |
| PR-159e | Email Channel CI gate (preflight + live SMTP) | After PR-159d |
| PR-162 (Codex) | Phase 5 Mocked closure | ✅ green on `42fb994` |

## Memory entries

No new memory entries needed. The existing
`feedback_phase5_mocked_keycloak_strategy.md` is the major learning
from this lane and is up to date as of `42fb994`'s green verdict.

## Non-goals

- No callers updated in this slice (PR-159d wires)
- No preference keys defined (PR-159c)
- No frontend change (PR-159c)
- No CI gate change (PR-159e)
- No migration (none needed for an in-memory abstraction)
- No `NotificationInboxService.createDirectNotification` rename or
  signature change — `InboxChannel` adapts to its existing shape
- No `EmailNotificationService.send` change — `EmailChannel` calls
  the existing 4-argument signature

## Bottom line

PR-159b is a small, additive abstraction slice. It adds two channel
implementations behind a single `NotificationChannel` interface and
a `NotificationDispatcher` that fans out to a configured channel
set. Zero existing-code changes; zero behavior change; new beans
register and stand ready for PR-159c/d to wire actual usage.

The dispatcher's exception-isolation guarantee (a failing channel
does not abort siblings) matches the AFTER_COMMIT @Async pattern
used elsewhere: notifications are downstream side effects, never
allowed to break the original request flow.

Total: ~336 lines added, 0 modified, 6 new unit tests.
