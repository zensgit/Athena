# PHASE368L Content Model Maintenance Surface

## Goal

Push Athena's content model line from "add-only authoring" to a real maintenance surface.

Claude's `Phase368K` established:

- backend sub-entity CRUD
- duplicate-name guards
- add dialogs for types, aspects, and properties

But the operator surface was still incomplete:

- no edit/delete entry points for selected types or aspects
- no delete path for properties
- no UI for constraint authoring despite backend support already existing
- no constraint delete action

This phase closes those operator-surface gaps without changing backend contracts.

## Scope

### Frontend Maintenance Actions

Updated [ContentModelsPage.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/pages/ContentModelsPage.tsx):

- added `Edit Type` dialog
- added `Edit Aspect` dialog
- added `Add Constraint` dialog
- added delete actions for:
  - selected type
  - selected aspect
  - individual properties
  - individual constraints
- added mutation-aware actions in the type/aspect explorer rather than leaving the page as add-only
- added dictionary-only guard messaging when the selected type/aspect does not belong to the currently selected model

The result is that the page now supports a maintenance loop:

- create
- inspect
- edit
- add property
- add constraint
- delete property
- delete constraint
- delete type/aspect

### Shared Constraint Utilities

Added [contentModelConstraintUtils.ts](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/utils/contentModelConstraintUtils.ts):

- `buildConstraintParameters(...)`
- `getConstraintValidationMessage(...)`
- `formatConstraintLabel(...)`

This keeps the page logic small and makes constraint authoring testable.

Supported authoring forms:

- `REGEX` via pattern
- `LIST` via comma/newline-separated values
- `RANGE` via min/max
- `LENGTH` via min/max

## Why This Phase Matters

`Phase368K` made the content model page authorable, but not maintainable.

That distinction matters because Alfresco-class model tooling is not just about adding definitions once. It is about being able to:

- refine titles/descriptions/parents
- evolve constraints
- remove bad definitions safely
- inspect and maintain a model over time

Without maintenance actions, Athena would still be closer to an internal prototype than a serious model-management surface.

## Files

- `ecm-frontend/src/pages/ContentModelsPage.tsx`
- `ecm-frontend/src/utils/contentModelConstraintUtils.ts`
- `ecm-frontend/src/utils/contentModelConstraintUtils.test.ts`

## Outcome

Athena's content model UI is no longer limited to:

- create model
- add type/aspect/property

It now supports real maintenance work on model sub-entities and constraints, which is a much closer operator posture to the reference target.
