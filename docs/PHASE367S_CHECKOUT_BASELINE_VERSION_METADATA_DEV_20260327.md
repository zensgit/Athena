# Phase367S: Checkout Baseline Version Metadata

## Goal

Push Athena closer to Alfresco working-copy/source semantics by capturing checkout baseline metadata at the moment of checkout, without introducing a full working-copy entity model yet.

## Scope

- Add persistent checkout baseline columns on `documents`:
  - `checkout_baseline_version_id`
  - `checkout_baseline_version_label`
- Capture baseline metadata during checkout.
- Clear or refresh baseline metadata during check-in / cancel checkout according to current lifecycle behavior.
- Expose baseline metadata through the virtual checkout relation and current search relations UI.

## Design

### Why baseline metadata first

Athena already supports:

- checkout / check-in / cancel checkout
- keep checked out
- caller-relative checkout diagnostics
- virtual checkout relation metadata

The next missing semantic is source lineage: “what version was this checkout taken from?”  
That is a useful operator-facing slice on its own and a sound foundation for a later full working-copy/source relationship model.

### Persistence behavior

`Document.checkout(...)` now captures:

- active checkout owner/date
- baseline version id from `currentVersion`
- baseline version label from `versionLabel` or the computed version string

`Document.checkin()` clears all checkout state including baseline metadata.

`keepCheckedOut` continues to refresh checkout state after version creation, so the checkout baseline naturally rolls forward to the latest checked-in version.

### Relation surface

The virtual checkout relation now includes:

- `checkoutBaselineVersionId`
- `checkoutBaselineVersionLabel`

`AdvancedSearchPage` relation details now render this as:

- checkout owner
- baseline version
- current version
- keep-checked-out support

This makes checkout lineage visible inside the existing relations mental model rather than scattering it into yet another isolated widget.

## Files

- `ecm-core/src/main/java/com/ecm/core/entity/Document.java`
- `ecm-core/src/main/resources/db/changelog/changes/035-add-document-checkout-baseline-columns.xml`
- `ecm-core/src/main/resources/db/changelog/db.changelog-master.xml`
- `ecm-core/src/main/java/com/ecm/core/controller/NodeController.java`
- `ecm-core/src/test/java/com/ecm/core/service/NodeServiceCheckoutTest.java`
- `ecm-core/src/test/java/com/ecm/core/controller/NodeControllerRelationsTest.java`
- `ecm-frontend/src/services/nodeService.ts`
- `ecm-frontend/src/pages/AdvancedSearchPage.tsx`

## Claude Code

Claude Code was used as a parallel design assistant to validate that baseline/source metadata was the smallest next working-copy-adjacent slice worth taking before a full working-copy entity model. Final implementation and validation were completed in this workspace.
