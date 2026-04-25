# P5 PR-146 — `run-scheduled-deliveries` Tx Isolation + Controller-Boundary Diagnostic

## Date
2026-04-26

## Scope

Backend-only fix. Resolves the notification lane CI gate failure that
PR-145's diagnostic could not surface, and removes the underlying
shared-transaction pollution that was producing it.

No frontend change. No migration. No new endpoint.

## Why this slice

PR-145 wrapped `RmReportPresetDeliveryService.runScheduledDeliveriesNow`
body in try/catch and rethrew as `IllegalStateException` so the new
`RestExceptionHandler` could surface the cause in the 500 body. The
next CI run on `13ab027` proved the wrapping was wired correctly
(Backend Verify exercised the new tests successfully) but the live
gate's 500 response **still** lacked a `message` field — the body
was Spring Boot's default error attributes, not our `ApiError`.

That meant the service-body catch was never invoked. The exception
was happening *outside* the service method body — specifically at
`@Transactional` commit time on the proxy, after the body returned.

### The actual failure mode

| Step | Where | Effect |
|------|-------|--------|
| 1. Class `@Transactional` wraps `runScheduledDeliveriesNow` | proxy | outer tx starts |
| 2. `processDueScheduledDeliveries` loops due presets | inside outer tx | — |
| 3. Per-preset `deliverPreset` throws (test sets `deliveryFolderId` to a document UUID; upload to non-folder fails) | inside outer tx | tx marked rollback-only |
| 4. Inner `try { deliverPreset } catch (Exception ex) { log.warn(...) }` | inside outer tx | exception swallowed; tx still rollback-only |
| 5. `runScheduledDeliveriesNow` body returns normally with swallowed count | inside outer tx | — |
| 6. `@Transactional` proxy attempts commit | proxy boundary | **`UnexpectedRollbackException` thrown** |
| 7. Exception propagates from proxy → controller → DispatcherServlet | outside service body | service-body try/catch never fires |
| 8. No matching `@ExceptionHandler` for `UnexpectedRollbackException` | servlet error path | Spring's default error renders 500 with no message |

PR-145's body-level catch was structurally unable to intercept (7).

## Design

Two coordinated changes in this commit:

### 1. Controller-boundary catch

Move the wrap-and-rethrow from the service body to the controller:

```java
@PostMapping("/run-scheduled-deliveries")
public ResponseEntity<...> runScheduledDeliveriesNow() {
    try {
        return ResponseEntity.ok(deliveryService.runScheduledDeliveriesNow());
    } catch (Exception ex) {
        String causeMessage = ex.getMessage() != null
            ? ex.getMessage()
            : ex.getClass().getSimpleName();
        throw new IllegalStateException(
            ex.getClass().getName() + ": " + causeMessage,
            ex
        );
    }
}
```

The controller is *outside* the service proxy, so this catch sees
both body-thrown and commit-thrown exceptions. The existing
`RestExceptionHandler.handleInternalState` (PR-145) then renders the
class+message in `ApiError.message`.

The PR-145 service-body try/catch is removed (now redundant — the
controller catch is a strict superset).

### 2. Tx isolation on the runner methods

Both the admin trigger and the cron-driven scheduler now declare
`@Transactional(propagation = NOT_SUPPORTED)`:

```java
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public ScheduledRunResultDto runScheduledDeliveriesNow() { ... }

@Scheduled(cron = "...")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public void runScheduledDeliveries() { ... }
```

`NOT_SUPPORTED` overrides the class-level `@Transactional`. With no
outer transaction wrapping the call:

- Each repository call (claim CAS, `presetRepository.save`,
  `executionRepository.save`, `auditService.logEvent` which is
  `REQUIRES_NEW`) gets its own implicit transaction via Spring Data's
  `SimpleJpaRepository` defaults
- A per-preset failure rolls back **only that preset's** transaction
- Other presets in the same loop iteration succeed independently
- The `UnexpectedRollbackException` cannot occur because there is no
  outer transaction to mark rollback-only

Side effect (positive): `persistFailedExecution` writes will now
actually commit when the per-preset path takes the FAILED branch —
previously they could be lost in the outer rollback.

## Verification

### Backend (delegated to CI — Docker not running locally)

Three tests in this commit:

**Service unit test** — `runScheduledDeliveriesNow` no longer wraps;
the underlying `RuntimeException` propagates as-is to the caller.
The wrapping responsibility is at the controller boundary now:
```java
RuntimeException ex = assertThrows(
    RuntimeException.class,
    service::runScheduledDeliveriesNow);
assertEquals(underlying, ex);
```

**Controller test 1** — service throws ordinary `RuntimeException`,
500 body contains class name + message:
```java
when(deliveryService.runScheduledDeliveriesNow())
    .thenThrow(new RuntimeException("scan failed: boom"));

mockMvc.perform(post(".../run-scheduled-deliveries"))
    .andExpect(status().isInternalServerError())
    .andExpect(jsonPath("$.message")
        .value(containsString("java.lang.RuntimeException")))
    .andExpect(jsonPath("$.message")
        .value(containsString("scan failed: boom")));
```

**Controller test 2 (the production failure mode PR-145 missed)** —
service throws `UnexpectedRollbackException`, 500 body still surfaces
its class+message:
```java
when(deliveryService.runScheduledDeliveriesNow())
    .thenThrow(new UnexpectedRollbackException(
        "Transaction silently rolled back because it has been marked as rollback-only"));

mockMvc.perform(post(".../run-scheduled-deliveries"))
    .andExpect(status().isInternalServerError())
    .andExpect(jsonPath("$.message")
        .value(containsString("UnexpectedRollbackException")))
    .andExpect(jsonPath("$.message")
        .value(containsString("rollback-only")));
```

### Local

- `git diff --check` clean on touched files
- Frontend tests not affected (no frontend change)
- Docker not available; backend tests delegated to CI

### Expected CI signal on `ae8c735`

| Job | Expected |
|-----|----------|
| Backend Verify | ✅ — three tests in this slice + all existing |
| Frontend Build & Test | ✅ unchanged |
| Phase C Security | ✅ unchanged |
| Acceptance Smoke | ✅ unchanged |
| **Frontend E2E Core Gate / notification step** | **✅ expected** — both tx isolation (no more rollback pollution) and controller-boundary catch (any remaining cause is now visible) should land it green |
| Phase 5 Mocked | Pre-existing cancelled |

If the gate is still red on `ae8c735`, the controller catch will
guarantee a `message` field in the 500 body — the next failure can
be diagnosed in one read of the artifact.

## Behavior change footprint

| Surface | Before | After |
|---------|--------|-------|
| Per-preset failure under admin trigger | Marks shared tx rollback-only; whole call 500s on commit; FAILED execution row may be lost in rollback | Per-preset isolated; outer call still returns 200 with `processedCount` reflecting successes; FAILED rows commit |
| Per-preset failure under cron tick | Same shared-tx pollution | Same isolation as above |
| Anything throwing RuntimeException out of `deliveryService.runScheduledDeliveriesNow` | Spring default 500 with no `message` | 500 body has `ApiError.message` = `<class>: <message>` |
| Other endpoints' uncaught `IllegalStateException` (telemetry anonymous-caller, etc.) | Stripped 500 body | Same as PR-145 — surfaces message |

No change to:
- Per-preset success / failed-execution-row schema
- The audit event shape (`AUDIT_SCHEDULED_DELIVERIES_TRIGGERED`) or
  trigger conditions
- The `ScheduledRunResultDto` shape (frontend consumers unchanged)
- Cron schedule, default cron expression, claim CAS query, or any
  preference key

## Files Changed

| File | Lines |
|------|-------|
| `ecm-core/.../controller/RmReportPresetController.java` | +14 / -1 |
| `ecm-core/.../service/RmReportPresetDeliveryService.java` | +18 / -16 (NOT_SUPPORTED + remove PR-145 body wrap) |
| `ecm-core/.../service/RmReportPresetDeliveryServiceTest.java` | +13 / -19 (test re-aimed at controller) |
| `ecm-core/.../controller/RmReportPresetControllerTest.java` | +35 / -13 (two coverage paths) |

## Slice sequence

| PR | Role | Status |
|----|------|--------|
| PR-145 | Body-level catch + IllegalStateException handler | ✅ shipped — handler is the surface used by this slice |
| **PR-146** | **Controller-boundary catch + tx isolation** | **✅ shipped this turn** |

## Next slice

If `ae8c735` notification gate goes ✅ → flip the notification lane
closeout from `pending` to `accepted` (single docs commit). Then
PR-147 (renumbered from yesterday's plan's PR-145) opens the email
delivery channel.

If `ae8c735` notification gate still ❌, the 500 body now carries the
exception class+message — read once, fix in PR-147, no more
diagnostic rounds.

## Non-goals

- Did not refactor `processDueScheduledDeliveries` to dispatch each
  preset through a `REQUIRES_NEW` self-injected proxy method. The
  `NOT_SUPPORTED` outer + Spring Data per-call implicit txs achieves
  the same isolation with one annotation.
- Did not add a generic `@ExceptionHandler(Exception.class)` —
  scope intentionally narrow.
- Did not touch the e2e test — it correctly expresses the intended
  behavior (deliver against a non-folder is a *delivery* failure,
  not a *trigger* failure; the 200 with FAILED ledger row is the
  expected path).
- Did not investigate Phase 5 Mocked cancellation (separate P2.A
  track).
