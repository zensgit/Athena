# Phase 1 P89 Design: Advanced Search Prefill Auto-Expand for Non-Basic Criteria

## Background
- Advanced Search dialog uses a single expanded accordion section.
- Previously, prefill always forced `Basic Search` to open.
- For saved searches that only contain non-basic criteria (for example `aspects`), users could see a mostly empty dialog and think prefill failed.

## Goal
- Improve prefill visibility by auto-expanding the most relevant non-basic section when basic criteria are absent.

## Scope
- Frontend UI behavior only (`SearchDialog` prefill effect).
- No API changes.

## Implementation
1. Compute which prefill buckets are populated
- File: `ecm-frontend/src/components/search/SearchDialog.tsx`
- Added booleans:
  - `hasBasicPrefill`
  - `hasDatePrefill`
  - `hasAspectsPrefill`
  - `hasPropertiesPrefill`
  - `hasMetaPrefill`

2. Resolve initial expanded accordion section
- Default remains `basic`.
- If no basic prefill exists, choose first populated section in priority order:
  - `dates`
  - `aspects`
  - `properties`
  - `meta`

3. Keep existing reset behavior unchanged
- Normal dialog open/reset flows are unaffected.
- The auto-expand rule applies only during saved-search prefill hydration.

## Test impact
- Extended saved-search E2E with a dedicated scenario:
  - only `aspects` prefilled
  - dialog auto-expands `Aspects`
  - expected checkbox is immediately visible and checked

