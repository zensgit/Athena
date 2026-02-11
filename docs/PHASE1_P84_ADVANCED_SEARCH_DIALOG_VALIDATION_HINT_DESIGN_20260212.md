# Phase 1 P84 - Advanced Search Dialog Validation Hint (Design) - 2026-02-12

## Background
- In Advanced Search modal, `Save Search` and `Search` buttons are disabled when no criteria are set.
- Users do not get explicit feedback on why actions are disabled.

## Scope
- Frontend-only usability improvement in Advanced Search dialog.
- E2E verification for disabled/enabled transitions.

## Changes
1. Add explicit criteria-required hint in dialog actions
- File: `ecm-frontend/src/components/search/SearchDialog.tsx`
- Added inline helper text when no criteria are provided:
  - `Add at least one search criterion to enable Save Search and Search.`

2. Reuse a single computed submit-state flag
- File: `ecm-frontend/src/components/search/SearchDialog.tsx`
- Introduced `canSubmitSearch` derived from `isSearchValid()`.
- Used the same flag for both `Save Search` and `Search` button disabled states.

3. Extend E2E for validation UX
- File: `ecm-frontend/e2e/search-dialog-preview-status.spec.ts`
- Added assertions for:
  - both action buttons disabled on empty criteria
  - criteria hint visible on empty criteria
  - both action buttons enabled after selecting preview status
  - criteria hint hidden once criteria become valid

## Risk and Mitigation
- Risk: text selector in E2E may become brittle if copy changes.
  - Mitigation: keep helper text concise and domain-specific; update selector alongside UX copy changes.

## Rollback
- Remove criteria hint block and `canSubmitSearch` wiring in `SearchDialog`.
- Revert added assertions in `search-dialog-preview-status.spec.ts`.
