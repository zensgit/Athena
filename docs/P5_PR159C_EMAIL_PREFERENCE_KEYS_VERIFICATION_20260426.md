# P5 PR-159c — notifyByEmail preference keys + channel resolver (design + verification)

## Date
2026-04-26

## Status
Implementation. Backend-only. Builds on `714a18b` (PR-159b NotificationChannel
abstraction, CI-verified on Backend Verify).

## Trigger

Backend Verify on `714a18b` (PR-159b) completed green → `NotificationDispatcher` and
both channel beans are CI-stable. PR-159c can build the preference layer on top with
confidence.

## Scope

Add the email opt-in preference keys and a single `resolveDeliveryChannels()` helper
that produces the `Set<String>` channel-ID argument for `NotificationDispatcher.dispatch()`.

**No wiring in this slice.** `publishSuccessfulScheduledDeliveryNotification` and
`publishFailedScheduledDeliveryNotification` continue to call
`activityService.postDirectNotificationActivity(...)` directly. Switching them to the
dispatcher is PR-159d.

## Why this slice exists

PR-159b introduced `NotificationDispatcher`, but no caller uses it yet. The dispatcher
takes `Collection<String>` channel IDs. The per-user email opt-in decision (inbox always,
email only if preferred) lives in `RmReportPresetDeliveryService` alongside the existing
`PREF_NOTIFY_ON_SUCCESS` / `PREF_NOTIFY_ON_FAILURE` reads.

PR-159c adds the two new preference keys and a testable resolver that encodes the opt-in
semantics, so PR-159d can wire it with a minimal diff and no logic to unit-test inline.

## Files changed

| File | Change | Lines |
|------|--------|------:|
| `ecm-core/src/main/java/com/ecm/core/service/RmReportPresetDeliveryService.java` | +2 constants, +2 private methods, +1 package-private method, +import | +46 |
| `ecm-core/src/test/java/com/ecm/core/service/RmReportPresetDeliveryServiceChannelResolutionTest.java` | new unit test class | +126 |

Total ~172 lines added, 0 lines removed. **Zero behavior change** — no existing call site modified.

## Design

### New constants (lines 60-61)

```java
static final String PREF_NOTIFY_BY_EMAIL_ON_SUCCESS =
    "org.athena.rm.reportPreset.delivery.notifyByEmailOnSuccess";
static final String PREF_NOTIFY_BY_EMAIL_ON_FAILURE =
    "org.athena.rm.reportPreset.delivery.notifyByEmailOnFailure";
```

Namespace matches the existing inbox keys (`org.athena.rm.reportPreset.delivery.*`).
`PreferenceService` stores them as arbitrary key-value pairs — no schema migration needed.

### `isEmailNotificationEnabled(owner, preferenceKey)` — opt-in semantics

Mirrors the existing `isNotificationEnabled()` but with the critical difference that
`NoSuchElementException` (preference absent) returns **`false`** rather than `true`.

| Condition | Return |
|-----------|--------|
| Preference = `Boolean.TRUE` or `"true"` | `true` |
| Preference = `Boolean.FALSE` or `"false"` | `false` |
| `NoSuchElementException` (absent) | `false` — email is opt-in |
| Any other exception | `false` — fail closed, warn-log |

Existing `isNotificationEnabled()` returns `true` on absent preference (inbox default ON).
Email must be the opposite: sending unexpected email is more disruptive than a missing
inbox ping, so we default to no email.

### `resolveDeliveryChannels(recipient, isSuccess)` → `Set<String>`

```java
Set<String> resolveDeliveryChannels(String recipient, boolean isSuccess) {
    String inboxPrefKey = isSuccess ? PREF_NOTIFY_ON_SUCCESS : PREF_NOTIFY_ON_FAILURE;
    String emailPrefKey = isSuccess ? PREF_NOTIFY_BY_EMAIL_ON_SUCCESS : PREF_NOTIFY_BY_EMAIL_ON_FAILURE;
    Set<String> channels = new LinkedHashSet<>();
    if (isNotificationEnabled(recipient, inboxPrefKey)) {
        channels.add(NotificationChannel.INBOX);
    }
    if (isEmailNotificationEnabled(recipient, emailPrefKey)) {
        channels.add(NotificationChannel.EMAIL);
    }
    return Set.copyOf(channels);
}
```

Package-private (no `private`) so the test class in the same package can call it directly
without reflection, and so PR-159d can call it from within the same class.

Returns an immutable `Set<String>`. The caller (PR-159d) passes this directly to
`NotificationDispatcher.dispatch(payload, channels)`.

## Tests

`RmReportPresetDeliveryServiceChannelResolutionTest` — 6 unit tests,
`@ExtendWith(MockitoExtension.class)`, no Spring context:

| Test | Subject |
|------|---------|
| `success_inboxOnly_whenEmailPrefAbsent` | Missing email pref → inbox only (opt-in default OFF) |
| `success_inboxAndEmail_whenEmailPrefTrueBoolean` | Email pref `Boolean.TRUE` → both channels |
| `failure_inboxAndEmail_whenEmailPrefStringTrue` | Email pref `"true"` → both channels (failure path) |
| `success_emptySet_whenInboxPrefExplicitlyFalse` | Both prefs false → empty set |
| `success_emailOnly_whenInboxFalseEmailTrue` | Inbox disabled, email enabled → email-only channel set |
| `success_emailExcluded_whenEmailPrefThrowsUnexpectedException` | Pref service error → email excluded, warn-log |

All tests construct a real `RmReportPresetDeliveryService` with a mocked `PreferenceService`.
The other mocks (`ActivityService`, `AuditService`, etc.) satisfy `@RequiredArgsConstructor`
but are never called — the tests are scoped purely to channel resolution logic.

## Sequencing

| PR | Role | Status |
|----|------|--------|
| PR-159 | Email backend foundation | ✅ green on `35c99ca` |
| PR-159a | `ObjectProvider<JavaMailSender>` refinement | ✅ green on `59c94bf` |
| PR-159b | NotificationChannel abstraction | ✅ green on `714a18b` |
| **PR-159c** | **notifyByEmail preference keys + channel resolver** | **✅ this turn — `ec5a16d`, pending push + Backend Verify** |
| PR-159d (next) | Wire delivery events through dispatcher | After PR-159c verdict |
| PR-159e | Email Channel CI gate (preflight + live SMTP) | After PR-159d |

## Security / production impact

- **No behavior change.** The new constants and methods are not invoked by any caller.
  `resolveDeliveryChannels()` is dead code until PR-159d wires it.
- `PREF_NOTIFY_BY_EMAIL_ON_*` preferences default to `false` — no email will be sent
  until a user explicitly enables the preference.
- No database migration. Preferences are stored via `PreferenceService` which already
  supports arbitrary key-value storage.

## What PR-159d will change

`publishSuccessfulScheduledDeliveryNotification` and
`publishFailedScheduledDeliveryNotification` will be refactored to:

1. Call `activityService.saveActivity(...)` for audit-only (keeps inbox activity record)
2. Call `resolveDeliveryChannels(recipient, isSuccess)` to get the channel set
3. Call `notificationDispatcher.dispatch(payload, channels)` to fan out

The dispatcher routes to `InboxChannel` (which calls `createDirectNotification`) and
optionally `EmailChannel` (which calls `EmailNotificationService.send`).

PR-159d requires injecting `NotificationDispatcher` into `RmReportPresetDeliveryService`
as a new constructor argument — that's the only existing-file change in that slice.

## Bottom line

PR-159c is a pure-addition preference-key slice. It adds two constants, one opt-in
preference reader, and one channel-set resolver — all untouched by existing code. Six
unit tests validate the opt-in/opt-out semantics exhaustively. PR-159d completes the
wiring with a single-file diff.
