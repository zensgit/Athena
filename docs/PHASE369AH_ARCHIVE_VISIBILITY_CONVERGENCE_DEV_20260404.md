# Phase369AH Archive Visibility Convergence DEV

## Goal

Make archive semantics real in live surfaces:

- normal browse/read paths must treat `archiveStatus=ARCHIVED` as hidden
- live search must exclude archived content by default
- archive mutations must refresh the search index so the new visibility rule is effective immediately
- archived content should stay reachable only through the dedicated archive workspace

## Backend scope

### Live node visibility

Added live-only repository variants in [NodeRepository.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/repository/NodeRepository.java) and rewired high-frequency live services to use `ArchiveStatus.LIVE`:

- [NodeService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/NodeService.java)
- [FolderService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/FolderService.java)
- [TagService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/TagService.java)
- [CategoryService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/CategoryService.java)
- [CommentService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/CommentService.java)
- [RatingService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/RatingService.java)
- [LockService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/LockService.java)

This closes the most obvious leak where archived nodes were still retrievable from live ID/path endpoints or from tag/category/comment/rating/lock surfaces.

### Search/query convergence

Added `archiveStatus` projection to [NodeDocument.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/search/NodeDocument.java) and applied default `archiveStatus=LIVE` filters in:

- [FullTextSearchService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/search/FullTextSearchService.java)
- [FacetedSearchService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/search/FacetedSearchService.java)
- [SearchIndexService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/search/SearchIndexService.java)

`includeDeleted=true` still only affects deleted filtering; it does not expose archived content. Archive visibility remains workbench-only in this phase.

### Archive mutation index sync

[ContentArchiveService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/ContentArchiveService.java) now updates Elasticsearch documents for every archived/restored node after `saveAll(scope)`. Without this, search documents would keep stale `archiveStatus` values and archived nodes would continue appearing in live search until some unrelated reindex happened.

## Frontend scope

[ContentArchivePage.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/pages/ContentArchivePage.tsx) no longer links archived rows into ordinary `/browse/:nodeId`. Archived content is now restored from the archive workspace first, then reopened through normal live surfaces.

## Explicitly deferred

- duplicate-name/archive-restore conflict handling
- archive-aware create/move capacity semantics
- policy-driven scheduled archival
- external cold-storage backends
- admin `includeArchived` query overrides for live search/browse
