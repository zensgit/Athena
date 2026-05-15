# Dictionary Service Shape Guards Design and Verification

## Context

The frontend service hardening line continues to close API boundaries where
the SPA HTML fallback or a malformed JSON payload can be treated as a valid
DTO. `dictionaryService` is the only frontend consumer of the data dictionary
read API (`/dictionary/types*`, `/dictionary/aspects*`). Before this slice
every method (`listTypes`, `getType`, `getTypeProperties`, `getTypeHierarchy`,
`getMandatoryAspects`, `listAspects`, `getAspect`, `getAspectProperties`)
trusted the response body shape and forwarded it directly to the typed
return value, so an `index.html` fallback (HTTP 200 with a string body) or a
backend regression that dropped a field would surface as a downstream type
error rather than a clear "unexpected response" failure.

## Backend Contract Evidence

`ecm-core/src/main/java/com/ecm/core/controller/DictionaryController.java`:

- Mounted at `/api/dictionary` and `/api/v1/dictionary`.
- `GET /dictionary/types` → `List<TypeDefinitionDto>`.
- `GET /dictionary/types/{qualifiedName}` → `TypeDefinitionDto`.
- `GET /dictionary/types/{qualifiedName}/properties` → `List<PropertyDefinitionDto>`.
- `GET /dictionary/types/{qualifiedName}/hierarchy` → `List<String>`.
- `GET /dictionary/types/{qualifiedName}/mandatory-aspects` → `List<String>`.
- `GET /dictionary/aspects` → `List<AspectDefinitionDto>`.
- `GET /dictionary/aspects/{qualifiedName}` → `AspectDefinitionDto`.
- `GET /dictionary/aspects/{qualifiedName}/properties` → `List<PropertyDefinitionDto>`.

`ecm-core/src/main/java/com/ecm/core/dto/TypeDefinitionDto.java`:

```
TypeDefinitionDto(UUID id, String name, String title, String description,
                  String parentName, String qualifiedName,
                  List<String> mandatoryAspects,
                  List<PropertyDefinitionDto> properties)
```

`id`, `name`, `qualifiedName` are non-null in the database. `title`,
`description`, `parentName` are nullable. `mandatoryAspects` and `properties`
both default to `List.of()` when the entity returns null.

`ecm-core/src/main/java/com/ecm/core/dto/AspectDefinitionDto.java`:

```
AspectDefinitionDto(UUID id, String name, String title, String description,
                    String parentName, String qualifiedName,
                    List<PropertyDefinitionDto> properties)
```

Same nullability profile as `TypeDefinitionDto` minus `mandatoryAspects`.

`ecm-core/src/main/java/com/ecm/core/dto/PropertyDefinitionDto.java`:

```
PropertyDefinitionDto(UUID id, String name, String title, String description,
                      PropertyDataType dataType,
                      boolean mandatory, boolean multiValued,
                      String defaultValue,
                      boolean indexed, boolean protectedField, boolean encrypted,
                      String qualifiedName,
                      List<ConstraintDefinitionDto> constraints)
```

`PropertyDataType` is an enum serialized as a string. The frontend mirror is
`PropertyDataType =
  'TEXT'|'MLTEXT'|'INT'|'LONG'|'FLOAT'|'DOUBLE'|'DATE'|'DATETIME'|'BOOLEAN'|
  'URI'|'NODEREF'|'QNAME'|'CATEGORY'|'LOCALE'|'CONTENT'` in
`ecm-frontend/src/services/contentModelService.ts`. The four `boolean`
primitives are always present. `title`, `description`, `defaultValue` are
nullable. `encrypted` is a non-null boolean on the wire; the frontend
contract marks it optional, so the guard accepts either an absent field or a
boolean.

`ecm-core/src/main/java/com/ecm/core/dto/ConstraintDefinitionDto.java`:

```
ConstraintDefinitionDto(UUID id, ConstraintType constraintType,
                        Map<String, Object> parameters)
```

`ConstraintType` is the enum `'REGEX'|'LIST'|'RANGE'|'LENGTH'` in
`contentModelService.ts`. `parameters` is always a plain JSON object on the
wire (defaults to `Map.of()` when the entity value is null).

## Design

- Export `DICTIONARY_UNEXPECTED_RESPONSE_MESSAGE` — a stable, user-safe
  phrasing aligned with sibling guarded services.
- Preserve the public API surface: same eight methods, same return types,
  same endpoint paths, and the existing `encodeURIComponent(qualifiedName)`
  behavior is left identical.
- Re-use the imported frontend types from `contentModelService.ts`
  (`TypeDefinition`, `AspectDefinition`, `PropertyDefinition`,
  `ConstraintDefinition`, `PropertyDataType`, `ConstraintType`) so the
  contract stays single-sourced.
- Validate every response with a structural guard at the boundary, throwing
  `Error(DICTIONARY_UNEXPECTED_RESPONSE_MESSAGE)` on any mismatch.

## Files Changed

- `ecm-frontend/src/services/dictionaryService.ts`
- `ecm-frontend/src/services/dictionaryService.test.ts`
- `docs/DICTIONARY_SERVICE_SHAPE_GUARDS_DESIGN_VERIFICATION_20260514.md`

## Guard Rules

### `ConstraintDefinition`

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `id` | string | yes | UUID, serialized as string |
| `constraintType` | `'REGEX' \| 'LIST' \| 'RANGE' \| 'LENGTH'` | yes | Closed union check |
| `parameters` | plain object | yes | `Record<string, unknown>`; array / null rejected |

### `PropertyDefinition`

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `id` | string | yes | UUID |
| `name` | string | yes | |
| `qualifiedName` | string | yes | |
| `dataType` | `PropertyDataType` union | yes | 15-value closed union |
| `mandatory` | boolean | yes | |
| `multiValued` | boolean | yes | |
| `indexed` | boolean | yes | |
| `protectedField` | boolean | yes | |
| `encrypted` | boolean | no | If present must be boolean |
| `title` | string \| null | no | Nullable |
| `description` | string \| null | no | Nullable |
| `defaultValue` | string \| null | no | Nullable |
| `constraints` | `ConstraintDefinition[]` | yes | Every entry guarded |

### `TypeDefinition`

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `id` | string | yes | UUID |
| `name` | string | yes | |
| `qualifiedName` | string | yes | |
| `title` | string \| null | no | Nullable |
| `description` | string \| null | no | Nullable |
| `parentName` | string \| null | no | Nullable |
| `mandatoryAspects` | `string[]` | yes | Always present, may be empty |
| `properties` | `PropertyDefinition[]` | yes | Every entry guarded |

### `AspectDefinition`

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `id` | string | yes | UUID |
| `name` | string | yes | |
| `qualifiedName` | string | yes | |
| `title` | string \| null | no | Nullable |
| `description` | string \| null | no | Nullable |
| `parentName` | string \| null | no | Nullable |
| `properties` | `PropertyDefinition[]` | yes | Every entry guarded |

### String list endpoints

`getTypeHierarchy` and `getMandatoryAspects` accept only arrays whose every
element is `typeof 'string'`. Any other payload (object envelope, HTML
fallback, mixed array) throws.

Anything that fails any of the above (HTML fallback string, non-array list
response, missing field, wrong-typed nullable field, non-plain-object
constraint parameters, unsupported `dataType` or `constraintType`) throws
`Error(DICTIONARY_UNEXPECTED_RESPONSE_MESSAGE)`.

## Test Coverage

`ecm-frontend/src/services/dictionaryService.test.ts` mocks `./api` and
asserts only observable behavior — return values, mocked call arguments
(including the `encodeURIComponent` path encoding), and thrown error
messages. No DOM or node access is performed.

- `listTypes`: success forwards `/dictionary/types`; HTML fallback rejected;
  malformed `mandatoryAspects` rejected.
- `getType`: success encodes `cm:content` → `cm%3Acontent`; non-string
  `qualifiedName` rejected.
- `getTypeProperties`: success forwards `/dictionary/types/{enc}/properties`;
  unknown `dataType` rejected; non-object `parameters` on a constraint
  rejected (covers the constraint nesting).
- `getTypeHierarchy`: success forwards the hierarchy path; non-string
  entry rejected; HTML fallback rejected (covers string-list HTML case).
- `getMandatoryAspects`: success forwards the mandatory-aspects path;
  non-array envelope rejected.
- `listAspects`: success forwards `/dictionary/aspects`; aspect entry with
  `properties: null` rejected; HTML fallback rejected.
- `getAspect`: success encodes the qualified name; nullable optional fields
  (`title`/`description` undefined, `parentName` null, empty `properties`)
  accepted; non-string `parentName` rejected.
- `getAspectProperties`: success forwards the aspect-properties path;
  non-boolean `mandatory` rejected; invalid `constraintType` rejected.

## Verification

### Targeted Service Test

Intended command:

```bash
cd ecm-frontend
CI=true npm test -- --runTestsByPath src/services/dictionaryService.test.ts --watchAll=false
```

Result: **PASS**. Re-run by Codex after Claude produced the files. The Claude
worktree had no local `node_modules`, so verification temporarily reused the
main worktree dependency cache through a symlink that was removed before
staging. `dictionaryService.test.ts` ran 22 tests, 0 failures.

### Full Frontend Gates

Intended commands:

```bash
cd ecm-frontend
npm run lint
CI=true npm run build
```

Result: **PASS**. `npm run lint` completed cleanly. `CI=true npm run build`
completed cleanly with the existing CRA bundle-size advisory.

### Remote CI

Not triggered. This slice is committed in the worktree only and not pushed,
per the task scope.

## Residual Work

- This slice does not add new dictionary product capability.
- Other frontend services may still need equivalent response-shape guards
  (the broader hardening line is ongoing).
