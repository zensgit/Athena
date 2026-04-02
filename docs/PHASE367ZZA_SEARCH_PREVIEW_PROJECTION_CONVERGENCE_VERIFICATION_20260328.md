# Phase367ZZA Search Preview Projection Convergence Verification

## Scope

Validate that search indexing now projects effective preview semantics from rendition-backed rules instead of raw preview field passthrough.

## Automated Validation

### Backend tests

Command:

```bash
cd ecm-core && mvn -q -Dtest=NodeDocumentLockProjectionTest,NodeDocumentCheckoutProjectionTest,NodeDocumentPreviewProjectionTest test
```

Result:

- Passed

Covered behaviors:

- expired locks remain hidden in search projection
- checkout metadata remains intact in search projection
- generic binary content indexes as `UNSUPPORTED`
- unsupported failures normalize to `UNSUPPORTED`
- applicable but unscheduled previews still keep the existing pending semantics via missing preview status

### Diff hygiene

Command:

```bash
git diff --check -- \
  ecm-core/src/main/java/com/ecm/core/search/SearchPreviewProjection.java \
  ecm-core/src/main/java/com/ecm/core/search/NodeDocument.java \
  ecm-core/src/test/java/com/ecm/core/search/NodeDocumentPreviewProjectionTest.java \
  docs/PHASE367ZZA_SEARCH_PREVIEW_PROJECTION_CONVERGENCE_DEV_20260328.md \
  docs/PHASE367ZZA_SEARCH_PREVIEW_PROJECTION_CONVERGENCE_VERIFICATION_20260328.md
```

Result:

- Pending until after doc write, then expected to pass

## Expected Functional Outcome

- search index projection distinguishes unsupported/non-applicable previews from pending previews
- search filters and facets can start consuming better preview semantics without new frontend changes
- search semantics move one step closer to the new rendition definition/applicability model

## Residual Gap

Athena still has further work before search is fully rendition-backed:

- full facet/result convergence on richer rendition state
- broader consumer migration off legacy preview-field assumptions
- complete lifecycle source-of-truth migration away from `Document.preview*`
