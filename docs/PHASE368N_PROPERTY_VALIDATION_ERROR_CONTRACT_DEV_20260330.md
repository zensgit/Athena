# PHASE368N Property Validation Error Contract

## Goal

Push `Phase368M` from "runtime enforcement exists" to "runtime enforcement has a stable API contract".

Claude's `368M` correctly wired:

- mandatory property enforcement
- default value application
- constraint validation

But the failure contract was still weak:

- `NodeService` threw a plain `IllegalArgumentException`
- controller consumers only got one concatenated message string
- no stable violation list existed for frontend/operator surfaces

This phase makes property enforcement failures structured and reusable.

## Scope

### Backend Exception Contract

Added [PropertyValidationException.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/exception/PropertyValidationException.java):

- extends `IllegalArgumentException` for backward compatibility
- carries `violations: List<String>`

Updated [NodeService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/NodeService.java):

- `enforceAspectProperties(...)` now throws `PropertyValidationException`
- the legacy message string remains
- the same violations are also exposed structurally

### Global Error Mapping

Updated [RestExceptionHandler.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/controller/RestExceptionHandler.java):

- added dedicated handler for `PropertyValidationException`
- `ApiError` now includes optional `details`
- property validation failures now return:
  - `message`
  - `details[]`

This keeps old consumers working while enabling richer ones.

### Focused Tests

Updated [NodeServicePropertyEnforcementTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/service/NodeServicePropertyEnforcementTest.java):

- key failure-path tests now assert `PropertyValidationException`
- tests also verify violation collection instead of only raw message fragments

Added [NodeControllerPropertyValidationTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/controller/NodeControllerPropertyValidationTest.java):

- `addAspect` returns `400` with `details[]`
- `updateNode` returns `400` with `details[]`

## Why This Phase Matters

Without a structured error contract, Athena still behaves like an internal service:

- enforcement exists
- but the caller gets one opaque string blob

Alfresco-class admin/operator surfaces need better failure semantics:

- readable violations
- machine-consumable lists
- stable error bodies across endpoints

This phase closes that gap without reworking the happy path.

## Files

- `ecm-core/src/main/java/com/ecm/core/exception/PropertyValidationException.java`
- `ecm-core/src/main/java/com/ecm/core/service/NodeService.java`
- `ecm-core/src/main/java/com/ecm/core/controller/RestExceptionHandler.java`
- `ecm-core/src/test/java/com/ecm/core/service/NodeServicePropertyEnforcementTest.java`
- `ecm-core/src/test/java/com/ecm/core/controller/NodeControllerPropertyValidationTest.java`

## Outcome

Athena now has structured property validation failures for aspect/type runtime enforcement.

That means `368M` is no longer just "validation exists"; it is now "validation failures are stable API data", which is materially closer to a product-grade content model runtime.
