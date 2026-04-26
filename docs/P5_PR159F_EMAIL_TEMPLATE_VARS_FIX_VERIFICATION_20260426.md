# P5 PR-159f — Email template vars consistency fix

## Date
2026-04-26

## Context

PR-159c seeded the default success email template with:

```text
Duration: ${durationMs}ms
```

The PR-159d dispatcher wiring passed the scheduled-delivery summary map
directly into `EmailNotificationService` as template variables, but that
summary did not include `durationMs`.

Because `EmailNotificationService` intentionally uses
`PropertyPlaceholderHelper(..., ignoreUnresolvablePlaceholders=true)`,
the missing variable would not fail fast. A successful scheduled report
email could render the literal text:

```text
Duration: ${durationMs}ms
```

## Fix

`RmReportPresetDeliveryService` now includes `durationMs` in both
scheduled-delivery notification summaries:

- `publishSuccessfulScheduledDeliveryNotification(...)`
- `publishFailedScheduledDeliveryNotification(...)`

The value is copied from `RmReportPresetExecution.durationMs`, falling
back to `0L` only if the execution is unexpectedly missing the field.

## Test Coverage

`RmReportPresetDeliveryServiceTest` now asserts that `durationMs` is
present in:

- the activity summary map passed to `ActivityService`
- the `NotificationPayload.templateVars` map passed to
  `NotificationDispatcher`

This covers both inbox activity metadata and email template rendering
inputs.

## Verification

### Local

```bash
git diff --check
```

Result: passed.

```bash
./mvnw -q -Dtest=RmReportPresetDeliveryServiceTest test
```

Result: not runnable in this local session because `ecm-core/mvnw`
delegates Maven to Docker and the Docker daemon is not running:

```text
Cannot connect to the Docker daemon at unix:///Users/chouhua/.docker/run/docker.sock.
```

### CI

CI is the authoritative backend verification for this environment.
Expected coverage:

- Backend Verify compile + unit tests
- Phase C startup/security stack
- Frontend Build & Test
- Acceptance Smoke
- Frontend E2E Core Gate
- Phase 5 Mocked Regression Gate

## Residual Risk

The email template engine still tolerates unresolved placeholders by
design. This fix aligns the seeded templates with the scheduled-delivery
payload, but future seeded templates need matching tests or a stricter
template validation gate.
