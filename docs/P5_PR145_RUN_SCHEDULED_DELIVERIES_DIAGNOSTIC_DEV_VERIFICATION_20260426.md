# P5 PR-145 — `run-scheduled-deliveries` 500 Diagnostic Slice

## Date
2026-04-26

## Scope

Diagnostic-only backend slice. Makes the notification lane's CI gate
failure on `122c9ca` self-describing instead of opaque, so the actual
product bug can be identified and fixed in a follow-up PR-146.

No frontend change. No migration. No new endpoint.

## Why this slice

The PR-122..133 notification lane's first live-CI run produced a
real product failure:

```
[chromium] › e2e/rm-report-preset-schedule.spec.ts:836:5
  RM failed scheduled preset delivery creates inbox notification
  → POST /api/v1/records/report-presets/run-scheduled-deliveries
    → HTTP 500 Internal Server Error
    → {"timestamp":...,"status":500,"error":"Internal Server Error",
       "path":"/api/v1/records/report-presets/run-scheduled-deliveries"}
```

The `122c9ca` failure artifact downloaded to `/tmp/e2e-artifacts`
contained `tmp/ci-e2e-logs/ecm-core.log` but only **200 lines** were
preserved — the application log had rotated and the exception
header (class + message) was already gone. We only had the bottom
of the stacktrace which started inside Spring AOP.

Spring Boot's default 500 error response strips both the message and
the stacktrace, so the response body was useless for diagnosis. Until
the cause is known, we cannot:

- finalize the notification lane (P0 in revised plan)
- start PR-145 email channel (P1) — was the *next* number, this slice
  takes it instead

## Design

### Make the 500 carry the cause

Two coordinated changes:

**1. `RmReportPresetDeliveryService.runScheduledDeliveriesNow`** —
wrap its body in try/catch:

```java
public ScheduledRunResultDto runScheduledDeliveriesNow() {
    try {
        // ... existing body ...
        return new ScheduledRunResultDto(processedCount, generatedAt);
    } catch (Exception ex) {
        log.error("runScheduledDeliveriesNow failed", ex);
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

The message format is intentional: `<fully-qualified-class>: <message>`.
The class name tells us the failure category at a glance
(`org.springframework.dao.DataIntegrityViolationException` vs
`com.ecm.core.exception.NodeNotFoundException` etc.) without needing
the stacktrace.

**2. `RestExceptionHandler`** — add a handler that surfaces the
message in the body:

```java
@ExceptionHandler(IllegalStateException.class)
public ResponseEntity<ApiError> handleInternalState(
    IllegalStateException ex, HttpServletRequest request
) {
    log.error("Internal state error at {}: {}",
        request.getRequestURI(), ex.getMessage(), ex);
    return build(HttpStatus.INTERNAL_SERVER_ERROR,
                 ex.getMessage(), request);
}
```

`ApiError` already includes `message` and `path` fields; the next CI
run's Playwright log will capture the body, which will tell us the
exception class and message immediately.

### Why not catch in the controller?

The controller is thin (one-line delegate). Catching at the service
boundary keeps the diagnostic close to the work, and the rethrow
preserves the original exception as `cause` so audit and stacktrace
both remain intact in logs.

### Why `IllegalStateException` and not a custom exception?

- Already broadly understood semantics: "operation failed at runtime
  due to an unexpected state"
- No new class to register / import
- `IllegalStateException` is otherwise routed to Spring's default
  500-without-message; this slice intentionally fills that gap once,
  for *all* 77+ existing throws of `IllegalStateException` across
  `ecm-core` services. Strictly better — no test asserted on the
  empty-body shape (verified with grep).

### Behavior change footprint

| Before | After |
|--------|-------|
| `IllegalStateException` thrown anywhere → 500 with body `{"error":"Internal Server Error"}` only | 500 with body `{... "message": "<class>: <msg>", ...}` |

This affects e.g. `getScheduledDeliveryTelemetry`'s anonymous-caller
guard. No test in the repo asserts on the previous stripped-body
shape (`grep` for `IllegalStateException` + `isInternalServerError`
returned only the new test added in this commit).

## Tests added

### Service (Mockito unit test)
```java
@Test
@DisplayName("runScheduledDeliveriesNow rethrows underlying failures as
            IllegalStateException with class+message")
void runScheduledDeliveriesNowRethrowsAsIllegalStateException() {
    RuntimeException underlying = new RuntimeException("scan failed: boom");
    when(presetRepository.findByScheduleEnabledTrueAndDeletedFalse...)
        .thenThrow(underlying);

    IllegalStateException ex = assertThrows(
        IllegalStateException.class,
        service::runScheduledDeliveriesNow);

    assertTrue(ex.getMessage().startsWith("java.lang.RuntimeException"));
    assertTrue(ex.getMessage().contains("scan failed: boom"));
    assertEquals(underlying, ex.getCause());
}
```

### Controller (MockMvc test)
```java
@Test
@DisplayName("runScheduledDeliveriesNow surfaces underlying failure
            class+message in 500 body")
void runScheduledDeliveriesNowSurfacesFailureBody() throws Exception {
    Mockito.when(deliveryService.runScheduledDeliveriesNow())
        .thenThrow(new IllegalStateException(
            "java.lang.RuntimeException: scan failed: boom",
            new RuntimeException("scan failed: boom")));

    mockMvc.perform(post(".../run-scheduled-deliveries"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.message")
            .value(containsString("java.lang.RuntimeException")))
        .andExpect(jsonPath("$.message")
            .value(containsString("scan failed: boom")));
}
```

## Verification

### Local
- Frontend tests not affected (no frontend change in this slice).
- Backend tests: **delegated to CI** — local Docker daemon is down,
  so `mvnw` (Docker-wrapped maven) cannot run. The two new tests
  follow established patterns from the same suite.
- `git diff --check` on the four touched files — clean.

### Expected CI signal on `13ab027`

| Job | Expected |
|-----|----------|
| Backend Verify | ✅ — two new green tests, all existing tests still pass |
| Frontend Build & Test | ✅ unchanged |
| Phase C Security | ✅ unchanged |
| Acceptance Smoke | ✅ unchanged |
| **Frontend E2E Core Gate / notification gate step** | **❌ still fails — but the failure body now contains the exception class+message** |
| Phase 5 Mocked | Pre-existing cancelled |

The notification gate step is *expected to still fail*. This slice
does not fix the underlying bug — it makes the bug visible. Once we
read the next failure body in CI logs, PR-146 can land the actual fix.

## What we will know after the next CI run

Reading the Playwright failure trace body, we will see the exception
class:

| Class | Likely cause |
|-------|--------------|
| `com.ecm.core.exception.NodeNotFoundException` | `findByIdAndDeletedFalse` returned empty for a deleted preset |
| `org.springframework.dao.DataAccessException` | `claimScheduledRun` CAS or telemetry query DB issue |
| `java.lang.IllegalArgumentException: ...` | Validation in `pushPresetAuthentication` or similar |
| `org.springframework.security.access.AccessDeniedException` | Security context push failed |
| `org.springframework.transaction.TransactionSystemException` | JPA flush failed during the audit log write |
| Anything else | Will tell us where to look |

The test setup for `:836` deliberately points the schedule's
`deliveryFolderId` at a *document* (not a folder), so the most
plausible class is something thrown by the upload pipeline when it
tries to add a child to a non-folder. But the `processDueScheduledDeliveries`
loop already catches `Exception` per-preset, so the upload failure
shouldn't propagate. The actual cause must be in
`runScheduledDeliveriesNow` *outside* the loop or *outside* the per-preset
try/catch — that's the diagnostic gap this slice closes.

## Files Changed

| File | Kind | Lines |
|------|------|-------|
| `ecm-core/.../service/RmReportPresetDeliveryService.java` | Modified | +18 / -2 |
| `ecm-core/.../controller/RestExceptionHandler.java` | Modified | +10 / 0 |
| `ecm-core/.../service/RmReportPresetDeliveryServiceTest.java` | Modified | +27 / 0 |
| `ecm-core/.../controller/RmReportPresetControllerTest.java` | Modified | +18 / 0 |

No frontend file changed. No migration. No script.

## Next slice (PR-146, blocked on this CI run)

Read the next CI failure body on `13ab027`'s notification gate step.
Identify the actual exception class. Land a targeted fix:

- If it's a validation hole on the `PUT /schedule` endpoint
  (e.g., should reject document-id as `deliveryFolderId`), close
  the validation gap.
- If it's a DB constraint, fix the data flow.
- If it's an audit/notification publish issue, isolate as in the
  established "publish failures don't affect ledger status" pattern.

Either way: PR-146 has a single, named, observable failure to chase
once `13ab027` runs.

## Non-goals

- Did not add a generic `@ExceptionHandler(Exception.class)` —
  too aggressive, can swallow Spring internals.
- Did not change the `ScheduledRunResultDto` shape — frontend
  consumers stay unchanged.
- Did not modify the e2e test — it correctly identifies a real
  failure.
- Did not investigate the Phase 5 Mocked cancellation (separate
  P2.A track).
