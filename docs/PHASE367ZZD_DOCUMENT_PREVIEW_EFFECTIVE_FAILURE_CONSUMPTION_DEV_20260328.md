# Phase 367ZZD: Document Preview Effective Failure Consumption

## Goal
- Align ordinary preview-read responses with the same effective preview semantics already used by search and node payloads.
- Stop returning `FAILED` for zero-source preview reads when the user-facing outcome is actually unsupported.
- Let `DocumentPreview` consume server-side preview failure category instead of reconstructing everything from older node fields.

## Scope
- `DocumentController` hash-enforced preview-read fallback
- `DocumentPreview` preview result consumption

## Design
- Updated `buildHashEnforcedDeclinedResult(...)` so zero-source readiness emits `status=UNSUPPORTED` and `failureCategory=UNSUPPORTED`, while stale-hash auto-repair declines remain `FAILED/TEMPORARY`.
- Extended `PreviewFailureClassifier` so operator-facing reasons like `SOURCE_EMPTY`, `STALE_HASH_MISMATCH`, and `HASH_ENFORCE_DECLINED` resolve to meaningful unsupported/temporary categories instead of falling through to permanent.
- Extended the frontend local `PreviewResult` type to include `failureCategory`.
- Updated `DocumentPreview` to include `serverPreview.failureCategory` in its effective preview status resolution chain.

## Why This Matters
- The ordinary preview endpoint now matches operator-facing semantics more closely.
- `DocumentPreview` no longer has to infer unsupported-vs-failed entirely from stale node payloads when the preview endpoint already knows better.
- This removes another small but visible semantic mismatch in Athena’s preview surface.
