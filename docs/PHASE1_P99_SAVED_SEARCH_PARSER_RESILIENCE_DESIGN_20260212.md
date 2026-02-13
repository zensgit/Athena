# Phase 1 P99 Design: Saved Search Parser Resilience

Date: 2026-02-12

## Background

- Imported/legacy saved-search payloads can contain malformed values:
  - invalid date strings
  - negative size fields
  - unknown preview status tokens
  - mixed list value types
  - malformed JSON strings
- Parser should keep valid fields and ignore invalid ones without blocking load flow.

## Goal

Harden saved-search criteria reconstruction so malformed fields are ignored safely while valid fields still prefill the dialog.

## Scope

- `ecm-frontend/src/utils/savedSearchUtils.ts`
- `ecm-frontend/src/utils/savedSearchUtils.test.ts`

## Implementation

1. List normalization hardened
- `normalizeList` now only accepts primitive values (`string`, `number`, `boolean`) in arrays.
- Object/array entries inside list fields are ignored.

2. Size field normalization hardened
- Replaced generic numeric parser with non-negative parser for `minSize`/`maxSize`.
- Negative and non-numeric values are ignored.

3. Date field normalization hardened
- Added `asDateString`:
  - trims input
  - keeps value only if `Date.parse` is valid.
- Applied to:
  - `createdFrom`, `createdTo`, `modifiedFrom`, `modifiedTo`
  - range object aliases (`createdRange`, `modifiedRange`)

4. Direct string fields normalized
- `createdBy`, `contentType`, and `folderId` now use trimmed/empty-safe parsing.

## Expected Outcome

- Malformed imported payloads no longer leak invalid values into `SearchCriteria`.
- Parsing remains backward-compatible and non-blocking for legacy saved searches.
