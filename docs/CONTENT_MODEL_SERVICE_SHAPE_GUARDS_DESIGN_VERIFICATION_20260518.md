# Content Model Service Shape Guards: Design and Verification

Date: 2026-05-18

## Scope

This slice hardens `ecm-frontend/src/services/contentModelService.ts`
against malformed runtime responses while preserving endpoint paths,
request payloads, query params, method names, and no-content delete
calls.

No backend code, UI page, route contract, or package file was changed.
`.env` was already modified before this slice and was not touched,
staged, or committed.

## Backend Contract

Backend sources checked:

- `ecm-core/src/main/java/com/ecm/core/controller/ContentModelController.java`
- `ecm-core/src/main/java/com/ecm/core/dto/ContentModelDefinitionDto.java`
- `ecm-core/src/main/java/com/ecm/core/dto/TypeDefinitionDto.java`
- `ecm-core/src/main/java/com/ecm/core/dto/AspectDefinitionDto.java`
- `ecm-core/src/main/java/com/ecm/core/dto/PropertyDefinitionDto.java`
- `ecm-core/src/main/java/com/ecm/core/dto/ConstraintDefinitionDto.java`
- `ecm-core/src/main/java/com/ecm/core/entity/ModelStatus.java`
- `ecm-core/src/main/java/com/ecm/core/entity/PropertyDataType.java`
- `ecm-core/src/main/java/com/ecm/core/entity/ConstraintType.java`

Controller mount:

- `@RequestMapping({"/api/content-models", "/api/v1/content-models"})`

Frontend relative paths remain unchanged:

- `GET /content-models`
- `GET /content-models/{modelId}`
- `POST /content-models`
- `PUT /content-models/{modelId}`
- `POST /content-models/{modelId}/activate`
- `POST /content-models/{modelId}/deactivate`
- `DELETE /content-models/{modelId}`
- `POST /content-models/{modelId}/types`
- `PUT /content-models/types/{typeId}`
- `DELETE /content-models/types/{typeId}`
- `POST /content-models/{modelId}/aspects`
- `PUT /content-models/aspects/{aspectId}`
- `DELETE /content-models/aspects/{aspectId}`
- `POST /content-models/types/{typeId}/properties`
- `POST /content-models/aspects/{aspectId}/properties`
- `DELETE /content-models/properties/{propertyId}`
- `POST /content-models/properties/{propertyId}/constraints`
- `DELETE /content-models/constraints/{constraintId}`

Closed enum values:

- `ModelStatus`: `DRAFT | ACTIVE | DISABLED`
- `PropertyDataType`: `TEXT | MLTEXT | INT | LONG | FLOAT | DOUBLE | DATE | DATETIME | BOOLEAN | URI | NODEREF | QNAME | CATEGORY | LOCALE | CONTENT`
- `ConstraintType`: `REGEX | LIST | RANGE | LENGTH`

## Design

Added the exported sentinel:

- `CONTENT_MODEL_UNEXPECTED_RESPONSE_MESSAGE`

Added runtime guards for:

- `ContentModelDefinition`
- `TypeDefinition`
- `AspectDefinition`
- `PropertyDefinition`
- `ConstraintDefinition`
- Lists and nested arrays inside those DTOs

Guard policy:

- Reject HTML fallback and non-object JSON where objects are expected.
- Require UUID/id/name/qualified-name fields to serialize as strings.
- Require closed enum values for model status, property data type, and
  constraint type.
- Require backend boolean fields on properties:
  `mandatory`, `multiValued`, `indexed`, `protectedField`, `encrypted`.
- Allow backend nullable text fields:
  `description`, `author`, `versionLabel`, `title`, `parentName`,
  `defaultValue`.
- Require `mandatoryAspects` to be `string[]`.
- Require `constraints`, `properties`, `types`, and `aspects` to be
  arrays with guarded nested items.
- Require constraint `parameters` to be a JSON object.

Delete methods remain intentional no-content calls and are not guarded.

## Test Coverage

Updated test file:

- `ecm-frontend/src/services/contentModelService.test.ts`

Covered cases:

- `listModels` guards nested model/type/aspect/property/constraint DTOs.
- `getModel` path forwarding.
- `createModel`, `updateModel`, `activateModel`, `deactivateModel`
  path and params preservation.
- `addType`, `updateType`, `addAspect`, `updateAspect` path and params
  preservation.
- `addPropertyToType` and `addPropertyToAspect` preserve encrypted
  property authoring payloads and guard responses.
- `addConstraint` preserves parameters object payloads and guards
  responses.
- Delete endpoints remain no-content calls.
- Rejections for HTML fallback, invalid model status, malformed
  mandatory aspects, malformed nested property booleans, invalid
  property data type, and malformed constraint parameters.

## Verification

Targeted Jest:

```bash
cd ecm-frontend
npm test -- --runTestsByPath src/services/contentModelService.test.ts --watchAll=false
```

Result:

```text
PASS src/services/contentModelService.test.ts
Test Suites: 1 passed, 1 total
Tests:       15 passed, 15 total
```

Frontend lint:

```bash
cd ecm-frontend
npm run lint
```

Result: PASS.

Diff hygiene:

```bash
git diff --check
```

Result: PASS.

## Commit

Pending commit at document write time:

- `fix(content-models): guard service responses`

## Follow-Up

The parallel `previewDiagnosticsService` core guard slice is being
developed in the Claude worktree
`.claude/worktrees/claude-preview-diagnostics-core-service-guards` and
will be reviewed and integrated separately.
