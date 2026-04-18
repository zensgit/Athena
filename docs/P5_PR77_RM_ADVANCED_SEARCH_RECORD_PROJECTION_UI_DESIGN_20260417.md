# P5 PR-77 RM Advanced Search Record Projection UI Design

## Scope

`PR-77` extends the shipped `PR-76` record-projection foundation into `AdvancedSearchPage`.

It does not add a new backend API. It consumes the same additive RM record projection already exposed through search results and renders it in the advanced-search result list.

## Problem

After `PR-76`:

- browse lists could show authoritative RM record state
- standard search results could show authoritative RM record state
- `AdvancedSearchPage` still rendered search cards without the RM record chip

That left one major search surface inconsistent with the rest of the `P5` search/index direction.

## Design

### 1. Reuse existing projection

`AdvancedSearchPage` now consumes the same RM record projection fields:

- `record`
- `declaredBy`
- `declaredAt`
- `declaredVersionLabel`
- `declarationComment`
- `recordCategoryId`
- `recordCategoryName`
- `recordCategoryPath`

No new request field or endpoint was introduced.

### 2. Reuse existing record-chip semantics

The page now reuses:

- `RecordStatusChip`
- `getRecordDeclarationFromNode(...)`

This keeps tooltip behavior aligned across:

- preview
- browse list/grid
- standard search results
- advanced search results

### 3. Thin UI-only slice

The result-card chrome is unchanged apart from the RM chip.

This slice intentionally does not add:

- RM-specific advanced-search filters
- RM-specific saved advanced-search presets
- new drilldown surfaces
- new backend protocol

## Files

- `ecm-frontend/src/pages/AdvancedSearchPage.tsx`
- `ecm-frontend/src/utils/recordDeclarationUtils.ts`
- `docs/P5_RM_INTAKE_OWNERSHIP_MATRIX_DEVELOPMENT_20260417.md`

## Non-Goals

- no backend changes
- no RM coverage signals in advanced search beyond record-state visibility
- no additional test harness for advanced-search page behavior in this slice
