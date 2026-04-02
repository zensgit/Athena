# Phase 367ZZC: Node DTO Effective Preview Semantics Verification

## Verified
- `GET /api/v1/nodes/{nodeId}` returns `UNSUPPORTED` for generic binary sources with no preview record.
- `GET /api/v1/nodes/{nodeId}` keeps applicable missing previews unset, preserving pending-compatible behavior.
- Unsupported failures are normalized from raw `FAILED` to effective `UNSUPPORTED` in ordinary node payloads.
- Search projection and ordinary node DTO now share the same helper, reducing semantic drift.

## Commands
```bash
cd ecm-core && mvn -q -Dtest=NodeControllerPreviewSemanticsTest,PreviewStatusFilterHelperTest,NodeDocumentPreviewProjectionTest,SearchControllerTest test
```

## Notes
- No frontend code change was required for this slice; existing consumers automatically benefit through the node payload.
- This is still a convergence layer over legacy `Document.preview*`; it does not yet make `RenditionResource` the sole lifecycle source of truth.
