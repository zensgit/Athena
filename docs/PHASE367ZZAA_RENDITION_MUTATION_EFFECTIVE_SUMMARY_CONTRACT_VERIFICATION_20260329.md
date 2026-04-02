# PHASE367ZZAA Rendition Mutation Effective Summary Contract Verification

## Commands
- `cd ecm-core && mvn -q -Dtest=RenditionResourceServiceTest,RenditionResourceControllerTest test`

## Assertions Covered
- `requeueForNode(...)` returns `invalidated=false`.
- Mutation results include `previewSummary`.
- Controller JSON exposes `previewSummary.previewStatus` and related fields for `requeue` and `invalidate`.

## Result
- Passed.
