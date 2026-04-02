# Phase 367ZZC: Node DTO Effective Preview Semantics

## Goal
- Carry effective preview semantics from rendition/search convergence into ordinary node APIs.
- Prevent browse/detail/document endpoints from exposing stale raw `Document.preview*` values while search endpoints already expose effective semantics.

## Scope
- Shared preview semantics helper
- `SearchPreviewProjection`
- `NodeDto`
- Node controller payload verification

## Design
- Added `PreviewStatusSemantics` as a shared helper for effective preview status/failure resolution.
- Refactored `SearchPreviewProjection` to delegate to the shared helper instead of maintaining a parallel implementation.
- Updated `NodeDto.from(...)` to emit effective `previewStatus` and `previewFailureReason`, while preserving `previewLastUpdated`.
- Kept `previewFailureCategory` derived from the effective values, so ordinary node payloads and search payloads now classify preview failures consistently.

## Why This Matters
- Browse/detail/document surfaces now converge with search on unsupported-vs-pending semantics.
- Athena ordinary node APIs now better reflect rendition applicability without forcing extra frontend calls.
- This closes another operator-level inconsistency that reference implementations often leave scattered across endpoint families.
