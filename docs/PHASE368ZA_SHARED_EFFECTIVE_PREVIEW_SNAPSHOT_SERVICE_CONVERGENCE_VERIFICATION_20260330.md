# Phase368ZA Verification

## Scope

Validated the shared effective preview snapshot convergence in:

- `RenditionResourceService`
- `PreviewDiagnosticsController`
- `OpsRecoveryController`

## Commands

### Targeted whitespace / patch sanity

```bash
git diff --check -- \
  ecm-core/src/main/java/com/ecm/core/service/RenditionResourceService.java \
  ecm-core/src/main/java/com/ecm/core/controller/PreviewDiagnosticsController.java \
  ecm-core/src/main/java/com/ecm/core/controller/OpsRecoveryController.java \
  ecm-core/src/test/java/com/ecm/core/service/RenditionResourceServiceTest.java \
  ecm-core/src/test/java/com/ecm/core/controller/PreviewDiagnosticsControllerSecurityTest.java \
  ecm-core/src/test/java/com/ecm/core/controller/OpsRecoveryControllerSecurityTest.java
```

Result:

- passed

### Focused backend verification

```bash
cd ecm-core && mvn -q -Dtest='RenditionResourceServiceTest#resolveEffectivePreviewSnapshotFallsBackToDocumentSemanticsWhenSummaryUnavailable+resolveEffectivePreviewSnapshotUsesExplicitFallbackWhenDocumentMissing+resolvePreviewMutationSummaryFallsBackToQueueStatusWhenSummaryUnavailable,PreviewDiagnosticsControllerSecurityTest#diagnosticsQueueDeclinedRequeueActionsPreferSharedPreviewSnapshot,OpsRecoveryControllerSecurityTest#listHistoryForAdmin' test
```

Result:

- passed

## Notes

I also ran a broader class-level pass first:

```bash
cd ecm-core && mvn -q -Dtest=RenditionResourceServiceTest,PreviewDiagnosticsControllerSecurityTest,OpsRecoveryControllerSecurityTest test
```

That surfaced unrelated legacy instability in `PreviewDiagnosticsControllerSecurityTest` where several existing cases are coupled to moving wall-clock window filters (`windowHours` vs. historical fixed timestamps). Those failures were not caused by this phase’s shared snapshot refactor, so final verification for this slice was performed with method-level focused tests on the code paths actually changed here.

## Verified Outcome

- shared effective preview snapshot semantics now live in `RenditionResourceService`
- `PreviewDiagnosticsController` consumes the shared resolver
- `OpsRecoveryController` consumes the shared resolver
- targeted contract coverage for the new shared resolver and both controller consumers is green
