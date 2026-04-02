# Phase 364: Rendition Resource API Verification

## Backend

- `cd ecm-core && mvn -q -Dtest=RenditionResourceSyncServiceTest,RenditionResourceServiceTest,RenditionResourceControllerTest,PreviewQueueServiceTest test`

## Scope verified

- Rendition resource collection filtering for `CREATED` and `NOT_CREATED`
- Single rendition fetch remains stable
- Preview-linked rendition `requeue` mutation envelope
- Preview and thumbnail `invalidate` mutation envelope
- Thumbnail invalidation stays narrower than preview invalidation
- Preview queue lifecycle persistence mirrors into `rendition_resources`

## Notes

- This slice intentionally keeps the old relation API and current preview queue implementation intact.
- Mutation semantics are scoped to the existing preview pipeline to avoid introducing a second, divergent rendition execution path.
