# P5 PR-149B - RM Notification Preference and Activity Tx Boundary Hardening

## Date

2026-04-26

## Scope

Backend-only hardening for the RM scheduled-delivery notification lane.

No frontend change. No migration. No endpoint or response-shape change.

This is a follow-up to PR-149. PR-149 moved each scheduled preset into
its own `REQUIRES_NEW` transaction. PR-149B prevents optional
notification preference reads and direct inbox publication from
marking that per-preset transaction rollback-only.

## Why This Slice

GitHub Actions run `24936133117` proved the fast preflight and backend
targeted tests were running, but the live RM notification acceptance
gate still failed in the two default-on scenarios:

```text
RM failed scheduled preset delivery creates inbox notification
RM successful scheduled preset delivery creates inbox notification
POST /api/v1/records/report-presets/run-scheduled-deliveries -> 500
```

The two explicit disabled-preference scenarios passed. That split is
important:

- disabled preference path reads an existing `false` value and skips
  notification publication
- default-on path treats a missing preference as `true`
- missing preference is represented by `PreferenceService.getPreference`
  throwing `NoSuchElementException`
- when that exception crosses a transactional proxy inside the
  scheduled-delivery transaction, Spring can mark the caller
  transaction rollback-only before the business catch converts it to
  default-on

There is a second optional side-effect boundary in the same path:
`ActivityService.postDirectNotificationActivity` saves an activity and
direct inbox notification. That publication must not be able to roll
back the delivery execution ledger if activity or inbox persistence
fails.

## Design

### 1. Missing Preference Reads Must Not Roll Back Callers

`PreferenceService.getPreference` now keeps its existing read-only
transaction but declares `NoSuchElementException` as non-rollback:

```java
@Transactional(readOnly = true, noRollbackFor = NoSuchElementException.class)
public Object getPreference(String username, String key) { ... }
```

This preserves the existing public API: callers such as
`PeopleController` still receive `NoSuchElementException` and map it
to not-found semantics. The change only affects transaction state for
callers that intentionally catch missing preferences, such as
`RmReportPresetDeliveryService.isNotificationEnabled`.

### 2. Direct Notification Activity Is Isolated

`ActivityService.postDirectNotificationActivity` now runs in an
independent transaction:

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public Activity postDirectNotificationActivity(...) { ... }
```

That method owns both the activity save and the direct inbox save. If
either fails, the notification-side transaction rolls back and the
scheduled-delivery transaction can still commit its execution row and
admin trigger audit.

Follower fan-out through `postActivity` is unchanged. Only direct
owner-scoped operational alerts use the new transaction boundary.

## Behavior Change

| Surface | Before | After |
|---------|--------|-------|
| Missing RM notification preference | `NoSuchElementException` is caught and defaults to enabled, but may mark the caller transaction rollback-only | Same default-on behavior without rollback-only pollution |
| Direct owner notification activity/inbox failure | Caller catches the exception, but shared transaction may already be rollback-only | Failure is contained in a separate notification transaction |
| Scheduled delivery execution ledger | Could be lost if optional notification read/write poisoned the per-preset tx | Delivery execution row can commit independently |
| People preference API | Missing preference still surfaces as not found | Unchanged |

## Files Changed

| File | Change |
|------|--------|
| `ecm-core/src/main/java/com/ecm/core/service/PreferenceService.java` | Add `noRollbackFor = NoSuchElementException.class` to `getPreference` |
| `ecm-core/src/main/java/com/ecm/core/service/ActivityService.java` | Add `REQUIRES_NEW` to `postDirectNotificationActivity` |
| `ecm-core/src/test/java/com/ecm/core/service/PreferenceServiceTest.java` | Contract test for missing-preference no-rollback boundary |
| `ecm-core/src/test/java/com/ecm/core/service/ActivityServiceTest.java` | Contract test for direct-notification `REQUIRES_NEW` boundary |

## Verification

### Static Checks

Command:

```bash
git diff --check
```

Result:

- passed

### Backend Test Attempt

Command:

```bash
cd ecm-core
./mvnw -q -Dstyle.color=never test -Dtest=ActivityServiceTest,PreferenceServiceTest,RmReportPresetDeliveryServiceTest
```

Result:

```text
Cannot connect to the Docker daemon at unix:///Users/chouhua/.docker/run/docker.sock. Is the docker daemon running?
```

Reason:

- this repository's `ecm-core/mvnw` delegates to the Docker image
  `maven:3.9-eclipse-temurin-17`
- local Docker daemon is not running in this environment
- backend unit verification must therefore be delegated to CI for this
  slice

### Tests Added

`ActivityServiceTest` now locks the direct-notification transaction
boundary:

```java
assertEquals(Propagation.REQUIRES_NEW, transactional.propagation());
```

`PreferenceServiceTest` now locks the missing-preference no-rollback
contract:

```java
assertTrue(List.of(transactional.noRollbackFor()).contains(NoSuchElementException.class));
```

Existing RM delivery tests already cover the business behavior that
notification publication exceptions must not change saved execution
status. The new tests cover the missing Spring transaction metadata
that those Mockito tests cannot simulate.

### Notification Closeout Preflight

Command:

```bash
scripts/p5-rm-notification-closeout-preflight.sh
```

Result:

- workflow YAML parse passed
- CI workflow wiring contract passed
- acceptance gate script syntax passed
- backend test class contract passed
- bare Playwright API `response.ok()` assertion scan passed
- Playwright discovery found the expected four
  `@rm-notification-acceptance` tests
- `peopleService.test.ts` passed 7/7 tests
- targeted `RecordsManagementPage.test.tsx` notification preference
  rollback tests passed 2/2
- script completed with `p5_rm_notification_closeout_preflight: ok`

## Expected CI Signal

| Job | Expected |
|-----|----------|
| Backend Verify | Green, including the two new transaction-boundary contract tests |
| Frontend Build & Test | Green, no frontend change |
| Phase C Security | Green, no security-surface change |
| Acceptance Smoke | Green, no app-shell change |
| Frontend E2E Core Gate / RM notification step | Green expected for all four `@rm-notification-acceptance` scenarios |
| Phase 5 Mocked Regression Gate | Independent from this backend slice |

If the RM notification acceptance gate is still red, the
controller-boundary diagnostic from PR-145/146 should surface the next
exception class and message in the response body.

## Non-Goals

- Did not start the email delivery channel.
- Did not change RM notification preference defaults.
- Did not change the Playwright acceptance scenarios.
- Did not widen generic exception handling.
- Did not modify Phase 5 Mocked specs.
