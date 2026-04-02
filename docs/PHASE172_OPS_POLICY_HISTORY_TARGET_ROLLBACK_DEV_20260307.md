# Phase 172 - Ops Policy History + Targeted Rollback (Development)

## Date
2026-03-07

## Goal
- Extend the unified ops policy center from "latest-only rollback" to "version-aware rollback":
  - expose policy version history API;
  - allow UI rollback to a selected target version;
  - keep diagnostics operator workflow fully covered in mocked E2E.

## Implemented

### 1) Backend: policy history query API
- Updated `OpsPolicyService`:
  - added `listHistory(domain, limit)` returning latest-first history entries with bounded limit.
  - added `DomainPolicyHistoryEntry` DTO.
- Updated `OpsPolicyController`:
  - added `GET /api/v1/ops/policies/{domain}/history?limit=20`
  - response includes:
    - `domain`
    - `currentVersion`
    - `history[]` with `version/updatedAt/actor/reason`

### 2) Frontend service contract
- Updated `ecm-frontend/src/services/opsPolicyService.ts`:
  - added `OpsPolicyHistoryEntry`, `OpsPolicyHistoryResponse`;
  - added `getHistory(domain, limit)` API client.

### 3) Preview diagnostics UI: selectable rollback target
- Updated `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`:
  - load policy history together with policy state;
  - add rollback target selector (`Policy rollback target version`);
  - rollback action now sends optional `targetVersion`;
  - add `Recent Policy Versions` table for operator visibility;
  - keep `Dry run` + reason-scope retry path from previous phase.

### 4) Playwright mock coverage update
- Updated `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`:
  - added mock for `GET /api/v1/ops/policies/{domain}/history`;
  - enhanced rollback mock to respect incoming `targetVersion`;
  - added assertions for:
    - history panel rendering;
    - rollback target button state (`Rollback to v2`);
    - rollback and dry-run action results.

### 5) Backend test expansion
- Updated `ecm-core/src/test/java/com/ecm/core/controller/OpsPolicyControllerSecurityTest.java`:
  - added admin-access verification for history endpoint;
  - ensured non-admin access is forbidden.
- Updated `ecm-core/src/test/java/com/ecm/core/service/OpsPolicyServiceTest.java`:
  - added history ordering + limit behavior test.

