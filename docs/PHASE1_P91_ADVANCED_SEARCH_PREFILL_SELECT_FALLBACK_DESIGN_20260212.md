# Phase 1 P91 Design: Advanced Search Prefill Select Fallback for Missing Facets

## Background
- Advanced Search uses `Select` for `Content Type` and `Created By`.
- Saved-search prefill can contain values not present in current facet lists (for example historical user, uncommon mime type, or facet API returning a narrower set).
- In that case, MUI `Select` may render blank even though state is set, causing confusion.

## Goal
- Ensure prefilled `Content Type` / `Created By` remain visible and selectable even when missing from facet options.

## Scope
- Frontend only (`SearchDialog`).
- Includes regression assertions in existing E2E saved-search prefill flow.

## Implementation
1. Add fallback-aware option lists
- File: `ecm-frontend/src/components/search/SearchDialog.tsx`
- Added computed lists:
  - `contentTypeOptions`
  - `createdByOptions`
- Behavior:
  - Start from facet options (or default content-type list when facets absent).
  - If current selected value is missing, append/inject that value into options.

2. Preserve label behavior
- `Content Type`:
  - when using default predefined list, keep friendly labels (`PDF`, etc.).
  - fallback values still render as raw mime strings.
- `Created By`:
  - render as raw username.

3. E2E assertion extension
- File: `ecm-frontend/e2e/saved-search-load-prefill.spec.ts`
- In legacy payload scenario, assert `Active Criteria` includes:
  - `Type: application/pdf`
  - `Creator: legacy-user`
- Confirms prefilled select state remains visible in dialog summary path.

