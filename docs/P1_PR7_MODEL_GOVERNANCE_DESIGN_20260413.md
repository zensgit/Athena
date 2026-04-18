# P1 PR-7 Model Governance Design

## Date
- 2026-04-13

## Status
- Implemented

## Objective
- Prevent destructive content-model mutations from reaching persistence when model graphs are invalid or live nodes still depend on the definitions being changed.

## Scope
- Add runtime validation for:
  - activation
  - structural mutation against active models
  - circular inheritance
  - missing parent definitions
  - deleting in-use types, aspects, and properties
- Expose validation failures through consistent API error payloads.

## Implemented Design

### Runtime Validation Service
- Added `RuntimeModelValidationService`.
- Validation is query-based and does not add governance tables.
- Service responsibilities:
  - block structural mutation when a model is `ACTIVE`
  - validate type/aspect inheritance graphs before activation
  - reject missing parent definitions unless an external parent exists in an active model
  - reject delete operations when live nodes still use the definition

### Live Usage Queries
- Added `NodeRepository` helpers for:
  - `typeQName`
  - aspect membership
  - JSONB property-key presence

### Service Integration
- `ContentModelService` now delegates validation before:
  - model activation
  - model delete
  - type/aspect create when model is active
  - type/aspect parent changes
  - type/aspect/property delete
  - constraint create/delete when the owning model is active

### API Surface
- `ModelValidationException` is returned as `400`.
- `RestExceptionHandler` now includes validation `details` for model-governance failures.

## Implemented Boundaries
- This increment covers model graph integrity and live-node dependency checks.
- It does not yet introduce a precomputed dependency graph for automation-rule payload references.
- It does not add import/export or model-editor UI work.

## Files
- `ecm-core/src/main/java/com/ecm/core/service/RuntimeModelValidationService.java`
- `ecm-core/src/main/java/com/ecm/core/service/ContentModelService.java`
- `ecm-core/src/main/java/com/ecm/core/controller/RestExceptionHandler.java`
- `ecm-core/src/main/java/com/ecm/core/repository/NodeRepository.java`
- `ecm-core/src/main/java/com/ecm/core/exception/ModelValidationException.java`

## Exit Conditions
- invalid activation fails before persistence
- circular inheritance is blocked
- missing parent definitions are blocked
- in-use type/aspect/property delete fails with explicit validation details
