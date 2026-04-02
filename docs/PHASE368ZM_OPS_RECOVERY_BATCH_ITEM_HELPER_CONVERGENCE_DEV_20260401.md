# Phase 368ZM — Ops Recovery Shared Batch Item Helper Convergence

> **Scope**: Unify invalid-entry branches and batch item builder within OpsRecoveryController
> **Date**: 2026-04-01

---

## 1. Problem Statement

`OpsRecoveryController` had **two construction patterns** for `RecoveryBatchItemDto`:

| Pattern | Where | Problem |
|---------|-------|---------|
| `buildRecoveryBatchItem()` (7-param helper) | Success, catch-exception paths | Correct — uses document context, resolves preview summary |
| **Inline `new RecoveryBatchItemDto(...11 nulls...)`** | Invalid entry key branches (replay + clear) | Duplicated, error-prone, easy to drift from the helper's field order |

The two inline sites (replay line 1929, clear line 2016) constructed a 11-field record
directly with all-null preview fields. Any change to the record's field list required
updating both inline sites manually — a maintenance hazard.

## 2. What Was Done

### New static helper: `buildFailedBatchItem(String message)`

```java
private static RecoveryBatchItemDto buildFailedBatchItem(String message) {
    return new RecoveryBatchItemDto(
        null,                   // documentId (no document context)
        JobState.FAILED,
        "FAILED",
        message,
        null,                   // previewStatus
        FailureCategory.UNKNOWN,
        null,                   // previewFailureReason
        null,                   // previewFailureCategory
        null,                   // previewLastUpdated
        0,                      // attempts
        null                    // nextAttemptAt
    );
}
```

### Replaced inline constructions

```diff
 // replayBatchInternal — invalid entry key
-results.add(new RecoveryBatchItemDto(null, FAILED, "FAILED", "Invalid...", null, UNKNOWN, null, null, null, 0, null));
+results.add(buildFailedBatchItem("Invalid dead-letter entry key: " + entryKey));

 // clearBatchInternal — invalid entry key
-results.add(new RecoveryBatchItemDto(null, FAILED, "FAILED", "Invalid...", null, UNKNOWN, null, null, null, 0, null));
+results.add(buildFailedBatchItem("Invalid dead-letter entry key: " + entryKey));
```

### After convergence

| `new RecoveryBatchItemDto(` site | Purpose |
|-----------------------------------|---------|
| `buildFailedBatchItem()` | Null-doc failures (invalid key, missing doc) |
| `buildRecoveryBatchItem()` | Document-context items (success, skip, catch) |

**Zero inline constructions** outside these two helpers.

## 3. Files Changed

| File | Change |
|------|--------|
| `controller/OpsRecoveryController.java` | +`buildFailedBatchItem()` static helper; replaced 2 inline sites |

### New Files

| File | Purpose |
|------|---------|
| `test/controller/OpsRecoveryBatchItemHelperTest.java` | 4 tests verifying helper existence, shape, and convergence |

### NOT Modified

All preview/search files untouched. `opsRecoveryService.ts` not modified this phase.

## 4. Constraint Note

Per user instruction, only `OpsRecoveryController.java` was unlocked from the hot-file
freeze for this phase. No global batch-item type unification was attempted —
`PreviewQueueBatchItem` and `PreviewQueueSearchBatchItem` in preview/search files
remain untouched.
