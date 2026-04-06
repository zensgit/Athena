# Phase 369AN: CMIS Query/Discovery Backbone

## Goal

Extend the CMIS browser binding from simple navigation into minimal read-only query capability.

## Scope

This phase adds a small CMIS SQL subset through the existing browser binding:

- `cmisselector=query`
- `SELECT * FROM cmis:document`
- `SELECT * FROM cmis:folder`
- `WHERE IN_FOLDER(...)`
- `WHERE cmis:name = '...'`
- `WHERE cmis:name LIKE '...'`
- `AND` combinations of the supported clauses
- `ORDER BY cmis:name|cmis:lastModificationDate|cmis:creationDate`

This phase does **not** implement:

- joins
- projections other than `SELECT *`
- `OR`
- full CMIS SQL parser compatibility
- AtomPub
- write operations

## Implementation

New service:

- [CmisQueryService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/cmis/CmisQueryService.java)

Updated contracts:

- [CmisModels.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/cmis/CmisModels.java) adds `QueryResponse`
- [CmisBrowserController.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/controller/CmisBrowserController.java) adds `cmisselector=query`

## Design Notes

- Query execution is repository-backed, not Elasticsearch-backed
- Query scope respects:
  - `deleted = false`
  - `archiveStatus = LIVE`
  - current-user read permissions
- `IN_FOLDER('root')` maps to top-level nodes
- `IN_FOLDER('/path')` resolves via existing folder path APIs

## Why This Cut

This keeps CMIS progress honest:

- `369AM` established protocol entrypoints and object navigation
- `369AN` adds the first searchable protocol surface
- later phases can add richer query coverage, mutations, and AtomPub without rewriting the backbone
