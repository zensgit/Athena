# Phase 369AM: CMIS Protocol Backbone

## Goal

Stand up the first CMIS-facing protocol surface without taking on full OpenCMIS integration yet.

## Scope

This phase intentionally implements a read-only browser-binding backbone only:

- `repositoryInfo`
- `typeChildren`
- `object` by `objectId` or `path`
- `children` for root folders and folder contents

It does **not** yet implement:

- AtomPub binding
- CMIS query
- write/mutation operations
- ACL, versioning, relationships, multi-filing, renditions, or TCK compliance

## Implementation

New CMIS package:

- [CmisModels.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/cmis/CmisModels.java)
- [CmisTypeManager.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/cmis/CmisTypeManager.java)
- [CmisObjectFactory.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/cmis/CmisObjectFactory.java)
- [CmisBrowserService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/cmis/CmisBrowserService.java)

New browser-binding controller:

- [CmisBrowserController.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/controller/CmisBrowserController.java)

Binding entrypoint:

- `/api/cmis/browser`
- `/api/v1/cmis/browser`

Supported selectors:

- `cmisselector=repositoryInfo`
- `cmisselector=typeChildren`
- `cmisselector=object`
- `cmisselector=children`

## Notes

- Repository root is represented as a virtual CMIS folder with object id `root`
- Root children are backed by Athena root folders
- Regular object and children navigation is bridged onto existing `NodeService` and `FolderService`
- Object payloads expose a minimal CMIS property map plus a few Athena-specific extension properties
