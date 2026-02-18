# Phase 63: Preview Status Unsupported Matching for MIME Parameters - Verification

## Date
2026-02-18

## Scope
- Verify backend preview-status logic treats parameterized unsupported MIME values as `UNSUPPORTED`.
- Verify both filter path and facet-count path.

## Command

```bash
cd ecm-core
mvn -Dtest=PreviewStatusFilterHelperTest,SearchAclElasticsearchTest test
```

## Result
- PASS
- `SearchAclElasticsearchTest`: 6 passed
- `PreviewStatusFilterHelperTest`: 2 passed
- Total: 8 passed, 0 failed, 0 errors

## Verified Behaviors
1. `FAILED` filter excludes legacy unsupported records with:
   - `mimeType=application/octet-stream; charset=binary`
   - `previewStatus=FAILED`
2. `UNSUPPORTED` filter includes both:
   - canonical `UNSUPPORTED` records
   - legacy `FAILED` records with unsupported MIME signals (including parameterized MIME)
3. Preview-status facet counts classify parameterized octet-stream records under `UNSUPPORTED`, not `FAILED`.

## Conclusion
- Backend preview-status filtering/faceting now handles MIME-parameter variants consistently.
