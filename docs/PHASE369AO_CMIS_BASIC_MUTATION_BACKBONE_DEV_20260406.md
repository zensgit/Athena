# Phase369AO CMIS Basic Mutation Backbone DEV

## Summary

This phase extends the CMIS browser binding from read-only navigation/query into minimal write capability.

The scope is intentionally narrow:

- `cmisaction=createFolder`
- `cmisaction=createDocument`
- `cmisaction=updateProperties`
- `cmisaction=deleteObject`

This is still a backbone phase. It does not attempt full CMIS browser binding form fidelity, content stream upload, AtomPub, or OpenCMIS compatibility.

## Backend

### Models

Updated [CmisModels.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/cmis/CmisModels.java) with:

- `MutationRequest`
- `MutationResponse`

`MutationRequest` keeps the minimal JSON contract small:

- object targeting: `objectId` or `path`
- parent targeting: `folderId` or `folderPath`
- create fields: `name`, `description`, `mimeType`, `contentLength`
- update fields: `properties`, `metadata`

### Service

Added [CmisMutationService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/cmis/CmisMutationService.java).

Design choice:

- do not create a new storage layer
- bridge directly onto existing [FolderService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/FolderService.java) and [NodeService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/NodeService.java)

Action behavior:

- `createFolder`
  - uses `FolderService.createFolder(...)`
  - supports CMIS virtual root via `folderId=root` or `folderPath=/`
- `createDocument`
  - uses `NodeService.createDocument(...)`
  - creates a metadata-only document skeleton, not a content-stream upload
  - optionally applies `description`, `properties`, and `metadata` via `updateNode(...)`
- `updateProperties`
  - maps:
    - `cmis:name` -> node `name`
    - `cmis:description` -> node `description`
    - `athena:metadata.*` -> node metadata
    - `athena:property.*` -> node properties
- `deleteObject`
  - uses `NodeService.deleteNode(nodeId, false)`
  - deliberately stays on soft delete for the backbone

### Controller

Updated [CmisBrowserController.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/controller/CmisBrowserController.java) to add POST mutation dispatch.

The CMIS browser binding now supports:

- `GET /api/v1/cmis/browser?cmisselector=...`
- `POST /api/v1/cmis/browser?cmisaction=...`

Error mapping is explicit for the mutation entrypoint:

- invalid request -> `400`
- missing object/folder -> `404`
- permission failure -> `403`

## Notes

- This phase uses JSON request bodies instead of the full CMIS browser binding form-parameter surface.
- This phase does not upload binary content streams.
- This phase does not yet expose move/copy/checkin/checkout/versioning CMIS actions.
