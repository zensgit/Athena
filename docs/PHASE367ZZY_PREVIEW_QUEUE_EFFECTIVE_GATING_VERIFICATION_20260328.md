# Phase367ZZY Preview Queue Effective Gating Verification

## Scope

Verified:

- `ecm-core/src/main/java/com/ecm/core/preview/PreviewQueueService.java`
- `ecm-core/src/test/java/com/ecm/core/preview/PreviewQueueServiceTest.java`

## Commands

```bash
cd ecm-core && mvn -q -Dtest='PreviewQueueServiceTest#skipsEnqueueForUnsupportedWhenNotForced+skipsEnqueueForEffectiveUnsupportedWhenStatusMissing+blocksEnqueueForPermanentFailureWhenNotForced+skipsEnqueueWithinQuietPeriodForFailedDocument+evaluateEnqueueReturnsDryRunDecisionWithoutMutatingDeclinedHistory+evaluateEnqueueTreatsEffectiveUnsupportedAsUnsupportedWhenStatusMissing' test
git diff --check -- \
  ecm-core/src/main/java/com/ecm/core/preview/PreviewQueueService.java \
  ecm-core/src/test/java/com/ecm/core/preview/PreviewQueueServiceTest.java \
  docs/PHASE367ZZY_PREVIEW_QUEUE_EFFECTIVE_GATING_DEV_20260328.md \
  docs/PHASE367ZZY_PREVIEW_QUEUE_EFFECTIVE_GATING_VERIFICATION_20260328.md
```

## Result

Focused verification passed.

## Notes

- This phase intentionally targets queue admission and dry-run evaluation only.
- Execution-path processing semantics were left unchanged to keep the slice low-risk and reviewable.
