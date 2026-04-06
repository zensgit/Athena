# Phase369AP CMIS Content Stream And Versioning Backbone DEV

## Summary

This phase extends the CMIS browser binding from metadata-only browsing/mutation into minimal content-stream and versioning semantics.

The scope is intentionally small:

- `cmisselector=content`
- `cmisaction=setContentStream`
- `cmisaction=checkOut`
- `cmisaction=checkIn`
- `cmisaction=cancelCheckOut`

This phase still does **not** attempt:

- full PWC/OpenCMIS semantics
- CMIS multi-filing or relationship APIs
- AtomPub write support
- complete version-history CMIS endpoints

## Backend

### Models

Updated [CmisModels.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/cmis/CmisModels.java) so `MutationRequest` can carry:

- `filename`
- `contentBase64`
- `comment`
- `majorVersion`
- `keepCheckedOut`

This keeps the browser-binding backbone JSON-based and avoids taking on full multipart/browser-form compatibility yet.

### Content/versioning bridge

Added [CmisContentVersioningService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/cmis/CmisContentVersioningService.java).

Design choice:

- reuse existing [ContentService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/ContentService.java), [VersionService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/VersionService.java), and [NodeService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/NodeService.java)
- do **not** create a new CMIS-specific versioning model

Behavior:

- `content`
  - streams the current document content
- `setContentStream`
  - decodes base64 payload
  - creates a new document version through `VersionService`
- `checkOut`
  - uses the existing document checkout state in `NodeService`
- `checkIn`
  - optionally creates a new version from `contentBase64`
  - then clears checkout (or keeps it checked out)
- `cancelCheckOut`
  - clears existing checkout state

### Object mapping

Updated [CmisObjectFactory.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/cmis/CmisObjectFactory.java) so document payloads now include basic versioning and checkout metadata:

- `cmis:isVersionSeriesCheckedOut`
- `cmis:isLatestVersion`
- `cmis:isMajorVersion`
- `cmis:versionLabel`
- `athena:checkoutUser`
- `athena:checkoutDate`
- `athena:contentId`
- `athena:contentHash`

Allowable actions now also surface:

- `canSetContentStream`
- `canCheckOut`
- `canCheckIn`
- `canCancelCheckOut`

### Controller

Updated [CmisBrowserController.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/controller/CmisBrowserController.java):

- read selector:
  - `cmisselector=content`
- write actions:
  - `setContentStream`
  - `checkOut`
  - `checkIn`
  - `cancelCheckOut`

The new content selector returns a streamed response with content type, filename, and content length headers derived from the current document state.

## Notes

- `contentBase64` is a backbone compromise, not a final protocol-fidelity decision.
- `checkOut/checkIn` currently bridge to Athena's existing checkout state rather than full CMIS PWC object semantics.
- A later phase can layer richer CMIS version-history and AtomPub write behavior on top of this without replacing the core bridge.
