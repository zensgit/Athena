# Phase367ZZN - Node Controller Mutation Rendition Convergence

## Goal

Finish the `NodeController` convergence line by routing mutation responses through the same rendition-backed `toNodeDto(...)` helper already used by node reads.

## Why

Phase367ZZL moved:

- `getNode`
- `getNodeByPath`
- `getChildren`
- `searchNodes`

toward `RenditionResourceService.summarizeDocument(...)`.

But the mutation responses in the same controller still returned raw `NodeDto.from(...)` for:

- create
- update
- move
- copy

That left `NodeController` with a read/write preview-semantics split.

## Design

### 1. Reuse `toNodeDto(...)` for mutation responses

File:

- `ecm-core/src/main/java/com/ecm/core/controller/NodeController.java`

Change the following endpoints to return `toNodeDto(...)`:

- `createNode`
- `updateNode`
- `moveNode`
- `copyNode`

This keeps the public payload shape unchanged while ensuring that document mutations no longer fall back to raw entity preview semantics.

### 2. Extend the focused semantics regression

File:

- `ecm-core/src/test/java/com/ecm/core/controller/NodeControllerPreviewSemanticsTest.java`

Add a `searchNodes` regression so both:

- node detail/list reads
- node search reads

are explicitly locked to rendition summary semantics.

## Result

After this phase, `NodeController` no longer splits preview semantics between read and mutation responses. Document nodes now use the same rendition-backed preview contract across all major node surfaces.
