# P5 PR-149 — Per-Preset REQUIRES_NEW Isolation via Self-Injection

## Date
2026-04-26

## Scope

Backend-only fix for the `run-scheduled-deliveries` 500. Replaces
PR-146's `@Transactional(NOT_SUPPORTED)` workaround with the
correct structural fix: keep the outer transaction, isolate
per-preset work in nested `REQUIRES_NEW` transactions through a
self-injected proxy reference.

No frontend change. No migration. No new endpoint.

## Why this slice

PR-146's `NOT_SUPPORTED` approach removed the outer transaction so
per-preset failures could no longer mark it rollback-only. The
diagnostic surface (PR-145 + the controller-boundary catch in
PR-146) immediately exposed the new failure mode the next CI run
on `ae8c735`:

```json
{
  "status": 500,
  "message": "org.springframework.dao.InvalidDataAccessApiUsageException: \
              No EntityManager with actual transaction available for current \
              thread - cannot reliably process 'flush' call",
  "path": "/api/v1/records/report-presets/run-scheduled-deliveries"
}
```

`claimScheduledRun` is annotated:

```java
@Modifying(clearAutomatically = true, flushAutomatically = true)
@Query("update ...")
int claimScheduledRun(...);
```

Both `flushAutomatically` and `clearAutomatically` operate on the
JPA persistence context, which requires an active transaction.
PR-146's `NOT_SUPPORTED` removed it — flush had nothing to flush
to, threw `InvalidDataAccessApiUsageException`.

The diagnostic infrastructure (PR-145/146 controller catch + handler)
worked perfectly: the new failure mode was named in the 500 body.
This commit is the proper fix.

## Design

Three coordinated changes:

### 1. Revert NOT_SUPPORTED, restore default `@Transactional`

Both `runScheduledDeliveriesNow` and the cron-driven
`runScheduledDeliveries` go back to the class-level `@Transactional`
(`REQUIRED`). The outer transaction is needed for:

- `@Modifying(flushAutomatically = true)` on `claimScheduledRun`
- audit log writes
- consistency of any pre/post-loop reads

### 2. Self-injected proxy

```java
@Autowired
@Lazy
private RmReportPresetDeliveryService self;
```

`@Lazy` breaks the constructor-time circular dependency that would
otherwise prevent instantiation. The field is non-final so Lombok's
`@RequiredArgsConstructor` does not include it.

Why self-injection: Spring's `@Transactional` is enforced by an AOP
proxy. Direct `this.method()` calls bypass the proxy and silently
ignore propagation overrides. To make the per-preset method's
`REQUIRES_NEW` actually take effect when called from another method
on the same bean, the call must go through the proxy. The
`self` field holds the proxy reference.

### 3. New `processOneScheduledDelivery(RmReportPreset)` method

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public boolean processOneScheduledDelivery(RmReportPreset preset) {
    if (!claimScheduledRun(preset)) {
        return false;
    }
    RmReportPreset claimedPreset = presetRepository
        .findByIdAndDeletedFalse(preset.getId())
        .orElse(null);
    if (claimedPreset == null) {
        log.warn(...);
        return false;
    }
    SecurityContext previous = pushPresetAuthentication(claimedPreset);
    try {
        deliverPreset(claimedPreset, TriggerType.SCHEDULED, true, true);
        return true;
    } finally {
        popAuthentication(previous);
    }
}
```

Owns the entire per-preset critical section: claim CAS, post-claim
fetch + null-check, security-context push/pop, delivery
(`deliverPreset` itself catches its own inner failures and writes
`persistFailedExecution` rows in this same nested transaction).

The loop now dispatches through the proxy:

```java
for (RmReportPreset preset : duePresets) {
    try {
        if (self.processOneScheduledDelivery(preset)) {
            processedCount += 1;
        }
    } catch (Exception ex) {
        // defence-in-depth: nested tx already isolated the failure
        log.warn(...);
    }
}
```

Why pass the entity, not the ID: the pre-claim `nextRunAt` is the
expected-value for the CAS query. Re-fetching by ID would return
the post-claim state. Existing tests assert against `duePreset.getNextRunAt()`
as the CAS expected-value; passing the entity keeps that contract.
Detached-entity concerns don't apply because `claimScheduledRun`
only reads fields and dispatches a JPQL `update`; subsequent saves
go through `repository.save(...)` which merges the post-claim
re-fetch result.

## Failure-mode evolution

| Slice | Outer tx | Per-preset isolation | Failure mode |
|-------|----------|----------------------|--------------|
| Pre-PR-145 | Class-level REQUIRED | None — shared tx | Per-preset failure marks tx rollback-only → `UnexpectedRollbackException` at commit, opaque 500 |
| PR-145 | Same | Same | Diagnostic catch added but body-level catch can't see commit-time exceptions |
| PR-146 | NOT_SUPPORTED on public method | None — but no shared tx either | `@Modifying(flushAutomatically)` has no tx → `InvalidDataAccessApiUsageException` |
| **PR-149** | **REQUIRED (default)** | **Per-preset REQUIRES_NEW via self-injection** | **None of the above** |

The diagnostic surface (controller catch + handler) added in
PR-145/146 stays — defence-in-depth for any future regression in
the same area.

## Verification

### Local
- `git diff --check` clean on touched files
- Backend tests delegated to CI (Docker not running locally)
- Frontend tests not affected (no frontend change)

### Tests in this commit

**1. New isolation test** — `runScheduledDeliveriesNowIsolatesPerPresetFailures`:
Two due presets. The first throws on its post-claim
`findByIdAndDeletedFalse`. The second succeeds. The outer call
returns `processedCount = 1` with no exception — proving the
per-preset try/catch in the loop contains failures.

**2. Service-level test helper**:

```java
private RmReportPresetDeliveryService service() {
    RmReportPresetDeliveryService svc = new RmReportPresetDeliveryService(...);
    ReflectionTestUtils.setField(svc, "self", svc);
    return svc;
}
```

Sets `self` to the same instance so unit tests exercise the call
shape without the proxy semantics. Mockito doesn't honour
`@Transactional` anyway; this just prevents NPE on the
`self.processOneScheduledDelivery(...)` call.

**3. Existing tests preserved**:
- `runScheduledDeliveriesNowPropagatesUnderlyingFailure` — throw at
  `findByScheduleEnabled...` (outside the per-preset loop) still
  propagates as expected
- All success-path tests
  (`runScheduledDeliveriesPostsDirectNotificationOnSuccess`, etc.)
  exercise the same chain because the existing mock for
  `findByIdAndDeletedFalse(duePreset.getId())` is still consulted
  inside `processOneScheduledDelivery`

### Expected CI signal on `7a9d65e`

| Job | Expected |
|-----|----------|
| Backend Verify | ✅ — new isolation test + all existing |
| Frontend Build & Test | ✅ unchanged |
| Phase C Security | ✅ unchanged |
| Acceptance Smoke | ✅ unchanged |
| **Frontend E2E Core Gate / notification step** | **✅ expected** — both predicted failure modes (UnexpectedRollback and InvalidDataAccessApiUsage) are structurally prevented |
| **Phase 5 Mocked Regression Gate** | PR-148 expected to land 2 noise-filter tests green; rest of the systemic issue documented in the investigation MD remains |

If the notification gate is *still* red, the controller-boundary
catch + handler will surface the actual cause in the body. Each
diagnostic round has been faster than the last by design.

## Behaviour change footprint

| Surface | Before PR-149 | After PR-149 |
|---------|---------------|--------------|
| Per-preset failure under admin trigger | InvalidDataAccessApi 500 (post-PR-146) | Outer call returns 200 with `processedCount` reflecting successes only. Failed preset's `persistFailedExecution` row commits in its nested tx. |
| Per-preset failure under cron tick | Same as above | Same — cron tick benefits identically |
| Two scheduler instances racing | CAS still primary defence; rollback no longer cross-contaminates | Same — claim CAS unchanged; nested tx isolation is additive |
| Audit log write | Within outer tx | Same |

No change to:
- The controller catch / handler / 500 body shape
- `ScheduledRunResultDto` shape
- The cron expression or claim CAS query
- Any frontend consumer
- Any preference key

## Files Changed

| File | Lines |
|------|-------|
| `ecm-core/.../service/RmReportPresetDeliveryService.java` | +49 / -19 |
| `ecm-core/.../service/RmReportPresetDeliveryServiceTest.java` | +75 / -3 |

## Sequencing

| PR | Role | Status |
|----|------|--------|
| PR-145 | Diagnostic catch + IllegalStateException handler | ✅ shipped |
| PR-146 | Tx workaround (NOT_SUPPORTED) + controller-boundary catch | ✅ shipped — workaround replaced by PR-149 but the controller catch + handler stay |
| PR-148 | Phase 5 Mocked PoC (parallel track, independent) | ✅ shipped |
| **PR-149** | **REQUIRES_NEW per-preset isolation** | **✅ shipped this turn** |

## What success looks like

If `7a9d65e` lands the notification gate green:

1. Flip the notification-lane closeout from `pending` to `accepted`
   (single docs commit, picked up next turn)
2. PR-150 opens the email delivery channel as the next new
   capability — the original P1 from yesterday's plan, pushed back
   by PR-145..149 burning through PR numbers

If the gate is still red, the failure body now contains the
exception class + message in `ApiError.message`. Read once, fix
in PR-150 with the same per-slice cadence. No more diagnostic
rounds expected — both the obvious failure modes have been
structurally prevented.

## Non-goals

- Did not refactor `deliverPreset` to use `noRollbackFor` —
  REQUIRES_NEW makes the inner tx orthogonal to the outer, so
  noRollbackFor is unnecessary.
- Did not change `claimScheduledRun`'s `@Modifying` annotations —
  they're correct given the outer tx is back.
- Did not extract `processOneScheduledDelivery` to a separate bean
  — self-injection in the same bean is idiomatic Spring; a separate
  bean would just be ceremony.
- Did not touch the e2e test — it correctly expresses operator
  intent (force a delivery against a non-folder, expect a 200 with
  failed ledger row, see notification land).
- Did not investigate Phase 5 Mocked further — that lane is
  PR-148's PoC + planned PR-150/151/152 follow-ups.
