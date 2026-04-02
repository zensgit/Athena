# Phase 367ZZB: Search Preview Effective Status Semantics Verification

## Verified
- Missing preview status on generic binary sources resolves to `UNSUPPORTED`.
- Missing preview status on applicable sources stays pending-compatible.
- Unsupported failures normalize from raw `FAILED` to effective `UNSUPPORTED`.
- Retryable failures remain `FAILED`.
- Search result mapping uses effective status/failure/category in both full-text and faceted paths.

## Commands
```bash
cd ecm-core && mvn -q -Dtest=PreviewStatusFilterHelperTest,NodeDocumentPreviewProjectionTest,NodeDocumentLockProjectionTest,NodeDocumentCheckoutProjectionTest,SearchControllerTest test
```

## Notes
- This slice is intentionally compatibility-safe: request/filter names did not change.
- The result is a semantic convergence layer over existing index data, not a breaking API change.
