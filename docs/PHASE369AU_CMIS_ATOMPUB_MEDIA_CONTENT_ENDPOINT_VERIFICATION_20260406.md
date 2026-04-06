# Phase 369AU: CMIS AtomPub Media/Content Endpoint Verification

## Verification

Run:

```bash
cd ecm-core && mvn -q -Dtest=CmisAtomPubControllerTest,CmisInteropSmokePackTest,CmisAtomPubSerializerTest test
git diff --check
```

## Expected Outcome

- `GET /api/v1/cmis/atom/media` streams the shared content payload with the resolved MIME type and filename.
- `PUT /api/v1/cmis/atom/media` delegates to shared `setContentStream` semantics and returns Atom XML mutation output.
- AtomPub read/write/content contracts remain aligned with the browser-side CMIS services.
