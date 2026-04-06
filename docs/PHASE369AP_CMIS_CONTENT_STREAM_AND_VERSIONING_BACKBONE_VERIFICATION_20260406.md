# Phase369AP CMIS Content Stream And Versioning Backbone Verification

## Backend

Focused tests:

```bash
cd ecm-core && mvn -q -Dtest=CmisContentVersioningServiceTest,CmisMutationServiceTest,CmisBrowserControllerTest,CmisBrowserServiceTest test
```

Expected coverage:

- content selector returns stream metadata for a document
- `setContentStream` creates a new version from base64 content
- `checkOut` delegates to existing document checkout state
- `checkIn` can create a version before clearing checkout state
- `cancelCheckOut` clears checkout state
- browser controller exposes `cmisselector=content`
- browser controller exposes `setContentStream/checkOut/checkIn/cancelCheckOut`

## Diff hygiene

```bash
git diff --check
```

## Manual sanity checks

1. `GET /api/v1/cmis/browser?cmisselector=content&objectId={documentId}` returns the current stream with document MIME type.
2. `POST /api/v1/cmis/browser?cmisaction=setContentStream` with `contentBase64` updates the document and returns version-aware object metadata.
3. `POST /api/v1/cmis/browser?cmisaction=checkOut` marks the document checked out.
4. `POST /api/v1/cmis/browser?cmisaction=checkIn` optionally with `contentBase64` clears checkout and optionally creates a new version.
5. `POST /api/v1/cmis/browser?cmisaction=cancelCheckOut` clears checkout without content update.
