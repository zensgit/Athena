# Phase367ZZAE Preview Queue Runtime Effective Semantics Verification

## Scope

Verified:

- `ecm-core/src/main/java/com/ecm/core/preview/PreviewQueueService.java`
- `ecm-core/src/test/java/com/ecm/core/preview/PreviewQueueServiceTest.java`

## Commands

```bash
cd ecm-core && mvn -q -Dtest=PreviewQueueServiceTest test
git diff --check -- \
  ecm-core/src/main/java/com/ecm/core/preview/PreviewQueueService.java \
  ecm-core/src/test/java/com/ecm/core/preview/PreviewQueueServiceTest.java \
  docs/PHASE367ZZAE_PREVIEW_QUEUE_RUNTIME_EFFECTIVE_SEMANTICS_DEV_20260329.md \
  docs/PHASE367ZZAE_PREVIEW_QUEUE_RUNTIME_EFFECTIVE_SEMANTICS_VERIFICATION_20260329.md
```

## Result

Focused verification passed.

## Notes

- The new tests specifically pin three priority rules:
  - explicit `TEMPORARY` category beats misleading unsupported-looking message text
  - explicit `UNSUPPORTED` status beats retry-looking timeout text
  - explicit `PERMANENT` category is preserved into auto-block and dead-letter even when the message looks temporary
- This phase intentionally targets queue runtime semantics only; it does not yet refactor the deeper preview lifecycle writer.
