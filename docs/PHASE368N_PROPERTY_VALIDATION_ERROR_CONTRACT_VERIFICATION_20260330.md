# PHASE368N Property Validation Error Contract Verification

## Verified

### Backend

Focused property validation regression passed:

```bash
cd ecm-core && mvn -q -Dtest=NodeServicePropertyEnforcementTest,NodeControllerPropertyValidationTest,NodeControllerAspectTest test
```

What this verified:

- `NodeService` now throws `PropertyValidationException` on enforcement failures
- violation lists are preserved on the exception object
- `addAspect` controller responses include `details[]`
- `updateNode` controller responses include `details[]`
- existing aspect endpoint behavior still works after the new exception/handler path

### Patch Hygiene

`git diff --check` passed for the phase files:

```bash
git diff --check -- \
  ecm-core/src/main/java/com/ecm/core/exception/PropertyValidationException.java \
  ecm-core/src/main/java/com/ecm/core/service/NodeService.java \
  ecm-core/src/main/java/com/ecm/core/controller/RestExceptionHandler.java \
  ecm-core/src/test/java/com/ecm/core/service/NodeServicePropertyEnforcementTest.java \
  ecm-core/src/test/java/com/ecm/core/controller/NodeControllerPropertyValidationTest.java \
  docs/PHASE368N_PROPERTY_VALIDATION_ERROR_CONTRACT_DEV_20260330.md \
  docs/PHASE368N_PROPERTY_VALIDATION_ERROR_CONTRACT_VERIFICATION_20260330.md
```

## Notes

This phase intentionally does not change the polymorphic `createNode` request contract. The current `NodeController` request body still uses abstract `Node`, so JSON create-path controller coverage remains a separate concern.

The value of this phase is narrower and deliberate:

- successful enforcement from `368M`
- plus a stable, structured failure contract for reachable mutation routes
