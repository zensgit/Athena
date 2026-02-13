# Phase 1 P103 Design: Backend Preview Status Canonicalization

Date: 2026-02-12

## Background

- Frontend already normalizes preview status aliases.
- Backend preview filter normalization previously only uppercased tokens and accepted unknown values.
- This can cause non-canonical alias values to diverge between client and server behavior.

## Goal

Canonicalize backend preview status filter input so canonical and alias variants behave consistently.

## Scope

- `ecm-core/src/main/java/com/ecm/core/search/PreviewStatusFilterHelper.java`
- `ecm-core/src/test/java/com/ecm/core/search/PreviewStatusFilterHelperTest.java`

## Implementation

1. Added backend known-status allow-list
- `READY`, `PROCESSING`, `QUEUED`, `FAILED`, `UNSUPPORTED`, `PENDING`

2. Added backend alias mapping
- `IN_PROGRESS`, `RUNNING` -> `PROCESSING`
- `WAITING` -> `QUEUED`
- `ERROR` -> `FAILED`
- `UNSUPPORTED_MEDIA_TYPE`, `UNSUPPORTED_MIME`, `PREVIEW_UNSUPPORTED` -> `UNSUPPORTED`
- tokens containing `UNSUPPORTED` -> `UNSUPPORTED`

3. Unknown token handling
- Unknown values are ignored rather than producing invalid query branches.

4. Added focused unit tests
- Alias normalization + unknown token ignore
- Unsupported variant consolidation

## Expected Outcome

- Backend filter semantics now match frontend normalization for preview status aliases.
