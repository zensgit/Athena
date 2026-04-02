# Phase 368ZM — Ops Recovery Batch Item Helper Convergence — Verification

> **Date**: 2026-04-01

---

## 1. Verification Matrix

| # | Claim | Status |
|---|-------|--------|
| 1 | `buildFailedBatchItem(String)` exists as static private method | PASS |
| 2 | Returns `RecoveryBatchItemDto` with null documentId | PASS |
| 3 | Returns `JobState.FAILED` and outcome `"FAILED"` | PASS |
| 4 | Returns `FailureCategory.UNKNOWN` | PASS |
| 5 | Returns 0 attempts and null nextAttemptAt | PASS |
| 6 | Message parameter is passed through | PASS |
| 7 | `buildRecoveryBatchItem(7 params)` instance method still exists | PASS |
| 8 | replayBatchInternal invalid-key branch uses `buildFailedBatchItem` | PASS |
| 9 | clearBatchInternal invalid-key branch uses `buildFailedBatchItem` | PASS |
| 10 | Zero inline `new RecoveryBatchItemDto(` outside the two helpers | PASS |
| 11 | Preview/search files not modified | PASS |
| 12 | opsRecoveryService.ts not modified | PASS |

## 2. Hot-File Constraint

Only `OpsRecoveryController.java` was modified (per authorized exception). All preview/search/rendition files untouched.

## 3. Test Inventory

### OpsRecoveryBatchItemHelperTest.java — 4 tests

```
  ✓ buildFailedBatchItem exists as a static private method
  ✓ buildFailedBatchItem returns FAILED state with null documentId
  ✓ buildRecoveryBatchItem exists as an instance method
  ✓ no inline RecoveryBatchItemDto construction outside helper methods
```

## 4. Full Regression

```
Phase 368ZM (Batch Item Helper):              4 tests ✓
Phase 368ZE (User Preferences):              20 tests ✓
Phase 368ZC-ZD (Rating / Likes):             15 tests ✓
Phase 368Y (Discovery API):                   6 tests ✓
Phase 368X (Association Operator Surface):     7 tests ✓
Phase 368W (Cross-Surface Entry):              4 tests ✓
Phase 368V (Admin Governance Surface):        10 tests ✓
Phase 368U (Operator Surface Convergence):     4 tests ✓
Phase 368T (Shared Links Enhancement):         9 tests ✓
Phase 368R (Node Associations):               10 tests ✓
Phase 368Q (Type Enforcement):                14 tests ✓
Phase 368O (Request Contract):                11 tests ✓
Phase 368M (Aspect Property Enforcement):     13 tests ✓
Phase 368K (Content Model Authoring):         53 tests ✓
Phase 361-365 (Content Model + Aspect):        6 tests ✓
Phase 364B (Lock Enhancement):                38 tests ✓
Phase 368A (Working Copy):                    54 tests ✓
Existing tests:                               23 tests ✓
────────────────────────────────────────────────────────
Total:                                       301 tests, 0 failures
BUILD SUCCESS
```

## 5. Construction Site Inventory (after convergence)

| Method | Type | Callers |
|--------|------|---------|
| `buildFailedBatchItem(msg)` | `static` | replayBatchInternal (invalid key), clearBatchInternal (invalid key) |
| `buildRecoveryBatchItem(7 params)` | `instance` | queueDocuments (success/skip/catch), replayBatchInternal (catch), clearBatchInternal (success/skip/catch) |

All `RecoveryBatchItemDto` construction is now channeled through exactly 2 methods.
