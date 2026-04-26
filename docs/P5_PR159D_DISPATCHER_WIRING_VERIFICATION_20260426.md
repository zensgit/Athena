# P5 PR-159d — NotificationDispatcher wiring into delivery event flow (design + verification)

## Date
2026-04-26

## Status
Implementation. Backend-only. Builds on `ec5a16d` (PR-159c preference keys +
channel resolver). Closes the PR-159 email notification lane — the dispatcher
and both channels are now wired and live for scheduled delivery events.

## Trigger

PR-159c (`ec5a16d`) committed green locally. The `resolveDeliveryChannels()`
helper is tested and ready. PR-159d wires it into the two delivery notification
publish methods.

## Scope

Replace the direct `activityService.postDirectNotificationActivity(...)` calls
in `publishSuccessfulScheduledDeliveryNotification` and
`publishFailedScheduledDeliveryNotification` with the two-step dispatcher
pattern:

1. `activityService.createNotificationActivity(...)` — REQUIRES_NEW isolated
   activity persist (audit record committed before dispatch)
2. `notificationDispatcher.dispatch(payload, channels)` — fans out to inbox
   and/or email based on `resolveDeliveryChannels(recipient, isSuccess)`

## Files changed

| File | Change | Lines |
|------|--------|------:|
| `ecm-core/src/main/java/com/ecm/core/service/ActivityService.java` | +`createNotificationActivity` (REQUIRES_NEW public method) | +17 |
| `ecm-core/src/main/java/com/ecm/core/service/RmReportPresetDeliveryService.java` | +`UserRepository`+`NotificationDispatcher` injection; refactor 2 notification methods | +43 |
| `ecm-core/src/test/java/com/ecm/core/service/RmReportPresetDeliveryServiceTest.java` | Update 6 notification tests; add `@Mock UserRepository`, `@Mock NotificationDispatcher` | +94/−54 |
| `ecm-core/src/test/java/com/ecm/core/service/RmReportPresetDeliveryServiceChannelResolutionTest.java` | Update `service()` constructor arity | +7/−1 |

## Design

### `ActivityService.createNotificationActivity` (new public method)

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public Activity createNotificationActivity(
    String activityType, String userId, String siteId,
    UUID nodeId, String nodeName, Map<String, Object> summary
) {
    return saveActivity(activityType, userId, siteId, nodeId, nodeName, summary);
}
```

**Why REQUIRES_NEW?** The delivery service runs within its own `REQUIRES_NEW`
transaction (per-preset `processOneScheduledDelivery`). The activity record must
be committed to the DB before `NotificationDispatcher.dispatch()` is called —
`InboxChannel` creates a `Notification` row referencing `Activity.id` via a FK.
If the activity row is only in-memory when `createDirectNotification` runs, the
FK insert would fail. `REQUIRES_NEW` on `createNotificationActivity` suspends the
outer delivery tx, commits the activity independently, then returns — the FK
constraint is satisfied.

The previous `postDirectNotificationActivity` was also `REQUIRES_NEW` for the
same reason. This preserves that isolation guarantee while separating the
audit-only persist from the routing logic.

### `RmReportPresetDeliveryService` new dependencies

```java
private final UserRepository userRepository;
private final NotificationDispatcher notificationDispatcher;
```

`UserRepository` is used to resolve the recipient's email address from their
username (`preset.getOwner()` is a username, not an email):

```java
String email = userRepository.findByUsername(recipient).map(User::getEmail).orElse(null);
```

If the user is not found or has no email, `email` is `null`. `EmailChannel` handles
this gracefully — it bails with a debug log when `recipientEmail` is blank, matching
the "user opted out / no email configured" pattern.

### Refactored notification publish flow

Both `publishSuccessfulScheduledDeliveryNotification` and
`publishFailedScheduledDeliveryNotification` now follow the same pattern:

```java
Set<String> channels = resolveDeliveryChannels(recipient, isSuccess);
if (channels.isEmpty()) return;  // both inbox and email disabled → no-op

// audit activity persisted in REQUIRES_NEW tx
Activity activity = activityService.createNotificationActivity(...);

// build immutable payload
NotificationPayload payload = NotificationPayload.builder()
    .type(ACTIVITY_TYPE)
    .recipientUserId(recipient)
    .recipientEmail(email)
    .preferredLocale("default")
    .activity(activity)
    .templateVars(summary)
    .build();

// fan out to resolved channels
notificationDispatcher.dispatch(payload, channels);
```

The outer `try/catch (Exception)` is preserved — a failing notification path
never breaks the delivery execution record.

### Channel routing behavior

| Inbox pref | Email pref | channels result | Dispatch |
|-----------|-----------|----------------|---------|
| absent (default=true) | absent (default=false) | `{"inbox"}` | InboxChannel only |
| true | true | `{"inbox","email"}` | InboxChannel + EmailChannel |
| false | true | `{"email"}` | EmailChannel only |
| false | false | `{}` | no-op (early return) |

EmailChannel delegates to `EmailNotificationService.send()` which is `@Async` —
the email send returns immediately and is fire-and-forget with its own
exception isolation.

## Tests updated

Six tests in `RmReportPresetDeliveryServiceTest` migrated from
`postDirectNotificationActivity` assertions to the new pattern:

| Test | Change |
|------|--------|
| `runScheduledDeliveriesPostsDirectNotificationOnSuccess` | Stubs `createNotificationActivity` → Activity; verifies payload type/recipient and dispatcher called |
| `runScheduledDeliveriesKeepsSuccessWhenSuccessNotificationPublishFails` | `doThrow` on `createNotificationActivity`; verifies execution still SUCCESS |
| `runScheduledDeliveriesSkipsDirectSuccessNotificationWhenDisabledByPreference` | Verifies `createNotificationActivity` + `dispatch` never called when inbox pref=false |
| `runScheduledDeliveriesPostsDirectNotificationOnFailure` | Stubs `createNotificationActivity` → Activity; verifies payload type/recipient and dispatcher called |
| `runScheduledDeliveriesKeepsFailureWhenFailureNotificationPublishFails` | `doThrow` on `createNotificationActivity`; verifies execution still FAILED |
| `runScheduledDeliveriesSkipsDirectFailureNotificationWhenDisabledByPreference` | Verifies `createNotificationActivity` + `dispatch` never called when inbox pref=false |

Both test files now inject `@Mock UserRepository userRepository` and
`@Mock NotificationDispatcher notificationDispatcher` and pass them to the
`service()` constructor factory.

## Sequencing

| PR | Role | Status |
|----|------|--------|
| PR-159 | Email backend foundation | ✅ green on `35c99ca` |
| PR-159a | `ObjectProvider<JavaMailSender>` refinement | ✅ green on `59c94bf` |
| PR-159b | NotificationChannel abstraction | ✅ green on `714a18b` |
| PR-159c | notifyByEmail preference keys + channel resolver | ✅ `ec5a16d` (pending CI) |
| **PR-159d** | **Wire dispatcher into delivery event flow** | **✅ this turn — `e1c36b7`, pending push + Backend Verify** |
| PR-159e (next) | Email Channel CI gate (preflight + Greenmail SMTP test) | After PR-159d verdict |

## Behavioral change vs. prior implementation

The old code had a single inbox gate:
```java
if (!isNotificationEnabled(recipient, PREF_NOTIFY_ON_SUCCESS)) return;
```
This silenced **all** channels (including any future email channel) when inbox was disabled.

The new code uses per-channel gating:
```java
Set<String> channels = resolveDeliveryChannels(recipient, isSuccess);
if (channels.isEmpty()) return;
```
This means a user with `inbox=false, email=true` will now receive email notifications
without an inbox record. This is the intended behavior — channels are independent —
but it is a behavioral change vs. the prior single-gate approach. The change is safe
in production because `PREF_NOTIFY_BY_EMAIL_ON_SUCCESS/FAILURE` default to `false`
(opt-in), so no user will unexpectedly receive emails without having enabled the pref.

## Security / production impact

- Email dispatch is now live for scheduled deliveries. However, the
  `PREF_NOTIFY_BY_EMAIL_ON_SUCCESS` / `_ON_FAILURE` preferences default to
  **false** (opt-in). No emails are sent until a user explicitly enables the
  preference via the Preference API.
- `ecm.email.enabled` defaults to `false` — `EmailNotificationService` bails
  early if the flag is off, providing a second layer of protection against
  accidental sends in non-email environments.
- `EmailNotificationService.send()` is `@Async` — email failures do not
  propagate to the delivery thread or affect execution status.
- No database migration. No new entities.

## What PR-159e covers

The remaining gap is a CI gate that exercises the email channel end-to-end
with a real SMTP server (Greenmail via testcontainers). PR-159e will:

1. Add `EmailChannelIntegrationTest` with an embedded Greenmail SMTP container
2. Verify a template email is actually received at the test inbox
3. Add a CI job or step that fails the gate if the email integration is broken

This gate is separate from Backend Verify (which only runs unit tests) and
requires Docker to be available in the CI runner.

## Bottom line

PR-159d is the final behavioral wiring slice. Scheduled delivery notifications
now route through `NotificationDispatcher` — inbox always (subject to pref),
email optionally (opt-in via preference). The full chain:

```
deliverPreset()
  → publishSuccessfulScheduledDeliveryNotification()
    → createNotificationActivity()     [REQUIRES_NEW, commits audit row]
    → resolveDeliveryChannels()        [reads prefs, returns Set<String>]
    → notificationDispatcher.dispatch()
        → InboxChannel → createDirectNotification()  [always, if pref on]
        → EmailChannel → emailNotificationService.send()  [if user opted in]
```

All exception paths are isolated — a failing channel never aborts siblings,
and a failing notification never breaks the delivery execution record.
