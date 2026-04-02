# Phase368P: Properties Dialog Aspect Initial Properties

## Goal

Close the last obvious operator gap after `Phase368O` by making `PropertiesDialog` able to submit initial aspect properties, instead of only attaching a bare aspect name and letting mandatory-property validation fail with no in-dialog recovery path.

## Scope

- Extend `PropertiesDialog` to render dictionary-backed aspect property inputs for the currently selected aspect.
- Submit typed aspect property payloads through the existing `nodeService.addAspect(nodeId, aspectName, properties)` contract.
- Surface structured property validation failures inside the dialog using the existing backend `message + details[]` error contract.

## Backend Contract Assumed

- `POST /api/v1/nodes/{nodeId}/aspects/{aspectName}` already accepts an optional JSON body of initial property values.
- `PropertyValidationException` is already mapped to a stable API error shape with `details[]`.
- `dictionaryService.listAspects()` already returns aspect property definitions, including defaults, mandatory flags, data types, and constraints.

## Implementation

### 1. Shared API error formatting

Added `ecm-frontend/src/utils/apiErrorUtils.ts`:

- `extractApiErrorResponse(error)`
- `formatApiErrorMessage(error, fallback)`

This keeps `PropertiesDialog` from falling back to generic local error strings when the backend already returned precise property validation details.

### 2. Shared aspect property form normalization

Added `ecm-frontend/src/utils/aspectPropertyFormUtils.ts`:

- `buildAspectInitialPropertyValues(aspect)`
- `getAspectPropertyListOptions(property)`
- `buildAspectPropertyPayload(aspect, values)`

Normalization rules:

- use property `qualifiedName` as request payload key
- preserve defaults in initial form state
- convert booleans to `true/false`
- convert numeric data types to numbers
- split multi-valued fields on commas/newlines
- omit empty optional values

### 3. Properties dialog operator surface

Updated `ecm-frontend/src/components/dialogs/PropertiesDialog.tsx`:

- derive `selectedAspectDefinition` from the dictionary aspect list
- initialize per-aspect form state when the operator changes selection
- render `Initial Aspect Properties` inputs when the selected aspect defines properties
- support:
  - boolean fields
  - date/datetime fields
  - numeric fields
  - list-constrained single-value fields
  - free-form and multi-valued text fields
- show defaults / constraints / multi-value hints as helper text
- submit the normalized payload through `nodeService.addAspect(...)`
- render structured inline dialog errors using `formatApiErrorMessage(...)`

## Why This Slice

Without this change, Athena had a backend contract for initial aspect properties but the main node properties operator surface still could not exercise it. That left a real product gap versus the new contract and forced users into a failure-first flow for mandatory aspect properties.

This slice makes the capability usable from the actual day-to-day admin/operator surface.
