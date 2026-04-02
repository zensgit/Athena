# Phase367ZZAF Preview Queue Status Effective Summary Contract Verification

## Scope

Verified:

- `ecm-core/src/main/java/com/ecm/core/preview/PreviewQueueService.java`
- `ecm-core/src/main/java/com/ecm/core/controller/DocumentController.java`
- `ecm-core/src/test/java/com/ecm/core/preview/PreviewQueueServiceTest.java`
- `ecm-core/src/test/java/com/ecm/core/controller/DocumentControllerPreviewRepairTest.java`

## Commands

```bash
cd ecm-core && mvn -q -Dtest=PreviewQueueServiceTest,DocumentControllerPreviewRepairTest test
git diff --check -- \
  ecm-core/src/main/java/com/ecm/core/preview/PreviewQueueService.java \
  ecm-core/src/main/java/com/ecm/core/controller/DocumentController.java \
  ecm-core/src/test/java/com/ecm/core/preview/PreviewQueueServiceTest.java \
  ecm-core/src/test/java/com/ecm/core/controller/DocumentControllerPreviewRepairTest.java \
  docs/PHASE367ZZAF_PREVIEW_QUEUE_STATUS_EFFECTIVE_SUMMARY_CONTRACT_DEV_20260329.md \
  docs/PHASE367ZZAF_PREVIEW_QUEUE_STATUS_EFFECTIVE_SUMMARY_CONTRACT_VERIFICATION_20260329.md
```

## Result

Focused verification passed.

## Notes

- Verification now pins that successful enqueue returns post-queue `PROCESSING` semantics.
- Unsupported decline paths now expose `previewFailureCategory=UNSUPPORTED`.
- `DocumentController.queuePreview(...)` now preserves queue-backed effective preview summary when rendition summary is absent.
