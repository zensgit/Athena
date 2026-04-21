# P5 PR-94 RM Controller Security Slice — @MockBean Fix

## Date
2026-04-21

## Scope

Single-file test fix. Closes a Backend Verify regression introduced by PR-92.

## Failure Context

CI run `24696987186` (commit `9809b51`) failed Backend Verify with:

```
[ERROR] Tests run: 3, Failures: 0, Errors: 3, Skipped: 0
<<< FAILURE! -- in com.ecm.core.controller.RecordsManagementControllerSecurityTest

java.lang.IllegalStateException: Failed to load ApplicationContext
  Caused by: UnsatisfiedDependencyException: Error creating bean with name
  'recordsManagementController': Unsatisfied dependency expressed through
  constructor parameter 1: No qualifying bean of type
  'com.ecm.core.service.RmReportPresetService' available.
```

## Root Cause

PR-92 (commit `06ed14d`) added a new endpoint
`POST /api/v1/records/report-presets/{presetId}/execute` to
`RecordsManagementController`. To dispatch a saved preset back to the right
report method, the controller was given a new constructor-injected
dependency: `RmReportPresetService`.

`RecordsManagementControllerSecurityTest` is a `@WebMvcTest` slice test. It
only loads `RecordsManagementController`, `RestExceptionHandler`, and
`TestSecurityConfig`, and mocks `RecordsManagementService`. The new
`RmReportPresetService` dependency had no `@MockBean` declaration, so Spring
could not satisfy the constructor and the context refused to start.

Full-context tests (`RecordsManagementControllerTest`) did not fail because
they run against the full Spring test context where the real bean is present.

## Fix

Add one `@MockBean` declaration:

```java
import com.ecm.core.service.RmReportPresetService;
...
@MockBean
private RmReportPresetService rmReportPresetService;
```

Nothing else changes. No test logic adjustment — the existing cases only
exercise endpoints that don't call the new service, so a vanilla mock is
sufficient.

## Verification

```bash
cd ecm-core && ./mvnw -B test -Dtest=RecordsManagementControllerSecurityTest
```

Result:

```
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

All three cases pass:
- endpoints require authentication
- non-admin users cannot access endpoints
- admins can access endpoints

## Files Changed

| File | Kind |
|------|------|
| `ecm-core/src/test/java/com/ecm/core/controller/RecordsManagementControllerSecurityTest.java` | +4 lines (import + @MockBean) |

No production code changes. No migration. No frontend.

## Expected CI Outcome

| Job | Expected |
|-----|----------|
| **Backend Verify** | **✅ restored to green** |
| Frontend Build & Test | ✅ unchanged |
| Phase C Security Verification | ✅ unchanged |
| Acceptance Smoke | ✅ unchanged |
| Frontend E2E Core Gate | ✅ unchanged |
| Phase 5 Mocked Regression Gate | Verified separately by PR-93 |

## Lesson

When adding a new constructor dependency to any controller that has a
`@WebMvcTest` slice test, the slice test's `@MockBean` list must be updated
in the same commit. Full-context tests do not catch this class of break.

## Non-goals

- No changes to the execute endpoint, preset service, or any other RM surface
- No change to `TestSecurityConfig`
- No broadening of the slice test's scope

This is the minimal fix that restores the Backend Verify signal.
