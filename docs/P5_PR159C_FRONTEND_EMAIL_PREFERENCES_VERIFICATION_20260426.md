# P5 PR-159c-frontend — Email notification preference UI + seed templates (design + verification)

## Date
2026-04-26

## Status
Implementation complete. Closes the PR-159 email notification lane frontend gap.
Committed `727bf2c` and pushed to `origin/main`.

## Scope

Two deliverables in one commit:
1. **Migration 085** — seed default `EmailTemplate` rows so the delivery pipeline works
   end-to-end without manual DB setup.
2. **Frontend email preference UI** — two opt-in switches in `RecordsManagementPage`
   matching the `notifyByEmailOnSuccess` / `notifyByEmailOnFailure` preference keys
   shipped in PR-159c backend.

---

## Migration 085 — Email template seed data

### File
`ecm-core/src/main/resources/db/changelog/changes/085-email-notification-seed-templates.xml`

### Templates inserted

| template_key | locale | html_body | Description |
|---|---|---|---|
| `rm.report_preset.delivery.succeeded` | `default` | false | Plain-text success notification |
| `rm.report_preset.delivery.failed` | `default` | false | Plain-text failure notification |

### Subject / body variables

**Success template**
- Subject: `Athena: ${presetName} delivered successfully`
- Body: uses `${presetName}`, `${filename}`, `${durationMs}`

**Failure template**
- Subject: `Athena alert: ${presetName} delivery failed`
- Body: uses `${presetName}`, `${message}`

These match the `summary` map assembled in `RmReportPresetDeliveryService.publishSuccessful/FailedScheduledDeliveryNotification()`.

### Registration
Added to `db.changelog-master.xml` after `084-email-notification-foundation.xml`.

### Rollback
Parameterized `<delete>` statements on `template_key + locale`.

---

## Frontend — Email notification preference UI

### File changed
`ecm-frontend/src/pages/RecordsManagementPage.tsx`

### New constants (lines 90-91)
```typescript
const RM_PRESET_DELIVERY_NOTIFY_BY_EMAIL_SUCCESS_KEY = `${RM_PRESET_DELIVERY_PREFERENCE_PREFIX}notifyByEmailOnSuccess`;
const RM_PRESET_DELIVERY_NOTIFY_BY_EMAIL_FAILURE_KEY = `${RM_PRESET_DELIVERY_PREFERENCE_PREFIX}notifyByEmailOnFailure`;
```

These match the backend keys:
- `org.athena.rm.reportPreset.delivery.notifyByEmailOnSuccess`
- `org.athena.rm.reportPreset.delivery.notifyByEmailOnFailure`

### State changes

`PresetDeliveryNotificationPreferencesState` extended:
```typescript
interface PresetDeliveryNotificationPreferencesState {
  notifyOnSuccess: boolean;
  notifyOnFailure: boolean;
  notifyByEmailOnSuccess: boolean;  // new — opt-in, default false
  notifyByEmailOnFailure: boolean;  // new — opt-in, default false
}
```

Default factory returns `false` for both email fields (opt-in, matching backend default).

### Load function
`loadPresetDeliveryNotificationPreferences` loads all 4 preferences in a single
`peopleService.getPreferences(username, prefix)` call (no extra HTTP request).
`resolveBooleanPreference(value, false)` handles absent/invalid values by returning
`false` for email prefs.

### Save handler
`updatePresetDeliveryNotificationPreference` now uses a `prefKeyMap` dictionary
instead of a binary `key === 'notifyOnSuccess' ? ... : ...` conditional:
```typescript
const prefKeyMap: Record<keyof PresetDeliveryNotificationPreferencesState, string> = {
  notifyOnSuccess: RM_PRESET_DELIVERY_NOTIFY_SUCCESS_KEY,
  notifyOnFailure: RM_PRESET_DELIVERY_NOTIFY_FAILURE_KEY,
  notifyByEmailOnSuccess: RM_PRESET_DELIVERY_NOTIFY_BY_EMAIL_SUCCESS_KEY,
  notifyByEmailOnFailure: RM_PRESET_DELIVERY_NOTIFY_BY_EMAIL_FAILURE_KEY,
};
```
The `setPreferences` response reconciliation also reads all 4 keys so the UI
stays in sync after any save.

### UI

A new **Email notifications** section appears below the existing **Inbox notifications**
section, separated by a `<Divider>`. The section contains two `<Switch>` controls:
- "Success email notifications" (`notifyByEmailOnSuccess`)
- "Failure email notifications" (`notifyByEmailOnFailure`)

Both are disabled while loading or while any preference is saving
(`presetDeliveryNotificationPreferenceSavingKey !== null`). This is consistent
with the inbox switches above.

The section description reads: "Opt in to receive email alerts for scheduled preset
deliveries. Requires email to be configured in your profile."

---

## Full PR-159 lane — delivery complete

| Slice | Commit | Delivers |
|---|---|---|
| PR-159 | `35c99ca` | `EmailTemplate` entity, service, migration 084 |
| PR-159a | `59c94bf` | `ObjectProvider<JavaMailSender>` optional sender |
| PR-159b | `714a18b` | `NotificationChannel` abstraction + dispatcher |
| PR-159c | `ec5a16d` | `notifyByEmail` preference keys + `resolveDeliveryChannels()` |
| PR-159d | `e1c36b7` | Dispatcher wired into delivery event flow |
| PR-159e | `122b52c` | Greenmail SMTP integration gate |
| **PR-159c-frontend** | **`727bf2c`** | Migration 085 seed templates + email pref UI |

---

## Security

- Email opt-in prefs default `false` — no emails sent without explicit user action
- `ecm.email.enabled` defaults `false` — server-level guard remains
- Migration 085 sets `created_by = 'system'` — seed rows are not associated with a user

## Non-goals (out of scope)

- Per-user locale preference (currently hardcoded `"default"`)
- HTML email templates / CSS inlined variants
- Frontend ScheduleReportPresetDialog email toggle (notification pref lives on the RM page, not the schedule dialog — consistent with inbox pref placement)
