# PHASE368G Content Model Operator Surface And Aspect Actions

## Goal

Turn Claude’s parallel `Content Model / Data Dictionary + Aspect System` backend drop from a backend-only capability set into a safe, operator-usable surface.

This phase closes three concrete gaps:

- `ContentModelController` and `DictionaryController` were returning raw JPA entities with bidirectional links.
- `NodeController` aspect add semantics did not match the frontend path used by `nodeService.addAspect(...)`.
- Athena still had no real admin/operator surface for content model inspection or node aspect mutation.

## Why This Phase

Claude’s delivery created the structural backend pieces:

- model/type/aspect/property/constraint entities
- services for model CRUD and dictionary lookup
- persisted node aspects

But that was not enough to call the capability production-ready.

The remaining gaps were operator-critical:

- raw entity responses were recursion-prone and unstable as API contracts
- the frontend aspect add path was mismatched with the backend endpoint shape
- there was no actual page to inspect models/dictionary definitions
- there was no node-level aspect add/remove surface beyond hidden service methods

So this phase focuses on turning that foundation into a stable contract plus a minimal but real UI loop.

## Scope

### Backend

Added DTO-backed contracts for content model and dictionary APIs:

- [ContentModelDefinitionDto.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/dto/ContentModelDefinitionDto.java)
- [TypeDefinitionDto.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/dto/TypeDefinitionDto.java)
- [AspectDefinitionDto.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/dto/AspectDefinitionDto.java)
- [PropertyDefinitionDto.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/dto/PropertyDefinitionDto.java)
- [ConstraintDefinitionDto.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/dto/ConstraintDefinitionDto.java)

Updated controllers to return DTOs instead of entities:

- [ContentModelController.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/controller/ContentModelController.java)
- [DictionaryController.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/controller/DictionaryController.java)

This removes direct entity graph leakage and gives the frontend a stable shape with:

- `qualifiedName`
- flattened property/constraint lists
- no `model -> types -> model` recursion

Also fixed node aspect contract mismatch in:

- [NodeController.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/controller/NodeController.java)

`POST /api/v1/nodes/{nodeId}/aspects/{aspectName}` now exists alongside the old query-param form, matching the already-shipped frontend client call.

### Backend Verification Surface

Added focused MVC coverage:

- [ContentModelControllerTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/controller/ContentModelControllerTest.java)
- [DictionaryControllerTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/controller/DictionaryControllerTest.java)
- [NodeControllerAspectTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/controller/NodeControllerAspectTest.java)

These tests specifically verify:

- DTO graph shape instead of raw entity graph
- qualified-name decoding
- type/aspect property projection
- path-based aspect add endpoint compatibility

### Frontend

Added content model and dictionary client layers:

- [contentModelService.ts](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/services/contentModelService.ts)
- [dictionaryService.ts](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/services/dictionaryService.ts)

Added a real admin explorer page:

- [ContentModelsPage.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/pages/ContentModelsPage.tsx)

This page gives Athena a live operator surface for:

- listing registered content models
- activating/deactivating models
- creating new models
- inspecting per-model types/aspects
- exploring dictionary type hierarchy, mandatory aspects, and properties
- exploring dictionary aspect properties

Wired it into navigation:

- [App.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/App.tsx)
- [MainLayout.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/components/layout/MainLayout.tsx)

And turned aspect persistence into an actual node operator action in:

- [PropertiesDialog.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/components/dialogs/PropertiesDialog.tsx)

`PropertiesDialog` now:

- loads active dictionary aspects
- shows human-readable aspect labels
- supports `Add Aspect`
- supports chip-level `Remove Aspect`

## Files

### Backend

- `ecm-core/src/main/java/com/ecm/core/controller/ContentModelController.java`
- `ecm-core/src/main/java/com/ecm/core/controller/DictionaryController.java`
- `ecm-core/src/main/java/com/ecm/core/controller/NodeController.java`
- `ecm-core/src/main/java/com/ecm/core/dto/ContentModelDefinitionDto.java`
- `ecm-core/src/main/java/com/ecm/core/dto/TypeDefinitionDto.java`
- `ecm-core/src/main/java/com/ecm/core/dto/AspectDefinitionDto.java`
- `ecm-core/src/main/java/com/ecm/core/dto/PropertyDefinitionDto.java`
- `ecm-core/src/main/java/com/ecm/core/dto/ConstraintDefinitionDto.java`
- `ecm-core/src/test/java/com/ecm/core/controller/ContentModelControllerTest.java`
- `ecm-core/src/test/java/com/ecm/core/controller/DictionaryControllerTest.java`
- `ecm-core/src/test/java/com/ecm/core/controller/NodeControllerAspectTest.java`

### Frontend

- `ecm-frontend/src/services/contentModelService.ts`
- `ecm-frontend/src/services/dictionaryService.ts`
- `ecm-frontend/src/pages/ContentModelsPage.tsx`
- `ecm-frontend/src/App.tsx`
- `ecm-frontend/src/components/layout/MainLayout.tsx`
- `ecm-frontend/src/components/layout/MainLayout.menu.test.tsx`
- `ecm-frontend/src/components/dialogs/PropertiesDialog.tsx`

## Outcome

This phase changes the status of Claude’s `Content Model / Aspect` drop from:

- backend primitives exist

to:

- API contracts are stable
- frontend can inspect and operate them
- aspect mutation is actually usable from the node UI

That is materially closer to Alfresco-style operator completeness than “entities, services, and tables exist”, and it prevents Athena from shipping a nominally present but practically unsafe content-model surface.
