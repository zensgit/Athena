# Phase367ZZK - Rendition Definition Action Capabilities

## Goal

Turn rendition action gating into an explicit backend capability contract instead of leaving it as implicit client-side assumptions.

## Why

Phase367ZZJ made the shared rendition registry dialog executable by adding `Requeue` and `Invalidate + Requeue`.  
However, the dialog still inferred action availability purely from local conditions such as `registered` and `applicable`.

That was not robust enough because:

- action capability is part of the rendition contract, not just a UI concern
- some definitions may be registered/applicable but still not support mutation
- operators need a concrete reason when actions are unavailable

## Design

### 1. Extend backend definition status contract

Files:

- `ecm-core/src/main/java/com/ecm/core/service/RenditionResourceService.java`
- `ecm-core/src/main/java/com/ecm/core/controller/RenditionResourceController.java`

Add to each definition status:

- `canRequeue`
- `canInvalidate`
- `mutationBlockedReason`

Current rules:

- not registered -> blocked
- not applicable -> blocked, prefer `applicabilityReason`
- non-mutable rendition keys -> blocked
- preview / thumbnail definitions -> allowed

### 2. Consume the contract in the shared dialog

File:

- `ecm-frontend/src/components/dialogs/RenditionDefinitionDialog.tsx`

Changes:

- show blocked reason directly in the dialog
- only render `Requeue` when `canRequeue`
- only render `Invalidate + Requeue` when `canInvalidate`

### 3. Align frontend typing

File:

- `ecm-frontend/src/services/nodeService.ts`

Add the three capability fields to `NodeRenditionDefinitionStatus`.

## Result

After this phase, rendition action availability is part of the shared definition contract itself. Every page using the shared dialog gets the same mutation affordances and the same blocking explanation.
