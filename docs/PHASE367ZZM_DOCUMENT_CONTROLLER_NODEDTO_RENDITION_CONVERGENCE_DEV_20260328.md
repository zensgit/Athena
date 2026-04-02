# Phase367ZZM - Document Controller NodeDto Rendition Convergence

## Goal

Make `DocumentController` mutation responses prefer rendition-backed preview semantics instead of returning raw `NodeDto.from(document)` output.

## Why

Phase367ZZL moved ordinary node endpoints onto `RenditionResourceService.summarizeDocument(...)`, but the high-frequency document mutation endpoints still returned:

- upload legacy
- revert to version
- checkout
- checkin
- cancel checkout

via plain `NodeDto.from(document)`.

That meant mutation responses could still lag behind the newer rendition-backed semantics used by:

- node detail
- node children
- node search
- relation and rendition-specific surfaces

## Design

### 1. Reuse the same preview override pattern in `DocumentController`

File:

- `ecm-core/src/main/java/com/ecm/core/controller/DocumentController.java`

Add a private `toNodeDto(Document document)` helper:

- build the baseline `NodeDto`
- load `renditionResourceService.summarizeDocument(document)`
- if a document-backed summary is present, overwrite preview fields with the summary
- otherwise fall back to the baseline DTO

### 2. Route mutation responses through the helper

Use the helper for all `NodeDto` responses emitted by `DocumentController`:

- legacy upload version/create flows
- revert-to-version
- checkout
- checkin
- cancel-checkout

## Result

After this phase, mutation responses stop lagging behind browse/detail/search node surfaces on preview semantics. Athena now uses the same rendition-backed preview contract both for ordinary node reads and for document lifecycle mutations.
