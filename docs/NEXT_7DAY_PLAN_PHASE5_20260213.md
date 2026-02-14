# Next 7-Day Plan - Phase 5 (Admin Tooling + Cross-Module Verification) - 2026-02-13

This plan targets Alfresco-aligned operator ergonomics across:
Mail Automation / Search / Version / Preview / Permissions / Audit.

Baseline note:
- Phase 1 P0 list in `docs/ALFRESCO_GAP_ANALYSIS_20260129.md` is already implemented and has regression gates.
- Phase 4 preview hardening/diagnostics exists; Phase 5 focuses on **admin UX + automation** to keep it sustainable.

## Guiding Principles

- Every day ships a vertical slice: **feature + automation + docs**.
- Prefer Playwright E2E for UI flows; keep at least one “no-backend required” mocked spec per critical admin page.
- Avoid breaking API contracts; if needed, document in the day’s design MD.

## Known Environment Constraint (Local Dev)

Some E2E specs require the full Docker stack (`ecm-core`, DB, ES, Keycloak) to be running.
If Docker is down, run the “mocked API” E2E specs to keep UI validation unblocked.

Local note (as observed on 2026-02-13):
- The mocked Preview Diagnostics E2E can be run against a static build server to avoid CRA dev-server rebuilds.

## Day 0 (Prereqs) - Keep Dev + E2E Unblocked

Why:
- Phase 5 is UI-heavy; if local disk/Docker is unhealthy, verification stalls and regressions slip.

Checklist:
- Disk: ensure >= 25Gi free on `/System/Volumes/Data` (macOS tends to fail builds earlier when near-full).
- Docker Desktop:
  - `docker version` works
  - `docker compose ps` works
- Backend health:
  - `curl -sS http://localhost:7700/actuator/health` returns 200
- Keycloak reachable:
  - `curl -sS http://localhost:8180/realms/ecm/.well-known/openid-configuration` returns 200
- Frontend:
  - dev server: `http://localhost:3000` (react-scripts)
  - prebuilt server: `http://localhost:5500` (static `build/`)

Fallback when Docker is unavailable:
- Run mocked API E2E specs against a static build server:

```bash
cd ecm-frontend
npm run build
(cd build && python3 -m http.server 5500)
ECM_UI_URL=http://localhost:5500 npx playwright test e2e/admin-preview-diagnostics.mock.spec.ts --project=chromium --workers=1
```

## Day 1 (P0) - Phase 54: Admin Preview Diagnostics UI

Goal:
- Admin-only page to list recent preview failures and expose safe actions.

Deliverables (implemented on branch `feat/phase5-preview-diagnostics-ui-20260213`):
- Dev doc: `docs/PHASE54_PREVIEW_DIAGNOSTICS_UI_DEV_20260213.md`
- Verification doc: `docs/PHASE54_PREVIEW_DIAGNOSTICS_UI_VERIFICATION_20260213.md`
- E2E:
  - `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts` (no backend required)
  - `ecm-frontend/e2e/admin-preview-diagnostics.spec.ts` (integration; requires backend)

Acceptance:
- Summary chips match backend samples.
- Retry/Force rebuild are disabled for UNSUPPORTED/PERMANENT failures.

Verification (PASS 2026-02-13):

```bash
cd ecm-frontend
(cd build && python3 -m http.server 5500)
ECM_UI_URL=http://localhost:5500 npx playwright test e2e/admin-preview-diagnostics.mock.spec.ts --project=chromium --workers=1
```

## Day 2 (P0) - Preview Diagnostics Hardening + Deep Links

Scope:
- Frontend + small API contract checks

Implementation (completed 2026-02-14):
- Add per-row quick actions:
  - Copy document id
  - Open parent folder (resolve by `item.path` -> `/folders/path` -> `/browse/:nodeId`)
- Improve deep link to Advanced Search:
  - include `previewStatus=FAILED|UNSUPPORTED` when present

Code touchpoints:
- `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`
- `ecm-frontend/src/pages/SearchPage.tsx` (or search routing target)
- `ecm-frontend/src/utils/searchPrefillUtils.ts` (if we extend URL prefill)

Verification:
- Extend `admin-preview-diagnostics.mock.spec.ts` to assert new actions (clipboard is stubbed for stability).
- Add/extend integration E2E if backend is available.

Mocked E2E should cover:
- Copy-to-clipboard for doc id (assert via init-script clipboard stub).
- Open parent folder navigates to `/browse/<folderId>` (folder resolved by path).
- Advanced Search deep link includes preview-status filters for FAILED vs UNSUPPORTED.

Integration E2E should cover:
- Create a retryable sample (TEMPORARY) and a permanent sample, then assert gating remains correct.

Docs:
- `docs/PHASE55_PREVIEW_DIAGNOSTICS_HARDENING_DEV_20260214.md`
- `docs/PHASE55_PREVIEW_DIAGNOSTICS_HARDENING_VERIFICATION_20260214.md`

Verification (PASS 2026-02-14):

```bash
cd ecm-frontend
npm run build
(python3 -m http.server 5500 --directory build)
ECM_UI_URL=http://localhost:5500 npx playwright test e2e/admin-preview-diagnostics.mock.spec.ts --project=chromium --workers=1
```

## Day 3 (P0) - Permissions: Permission-Set UX Parity

Goal:
- Make Alfresco-style permission sets obvious in the UI:
  - Coordinator / Editor / Contributor / Consumer

Implementation:
- Permissions dialog: display permission-set mapping alongside raw permissions.
- Add tooltip/help text describing what each set implies.

Code touchpoints:
- `ecm-frontend/src/components/dialogs/PermissionsDialog.tsx`
- `ecm-frontend/src/utils/permissionUtils.ts` (or similar mapping helpers)

Verification:
- Playwright: open permissions dialog, assert permission sets render for admin.

Mocked E2E option (preferred):
- Add a mocked spec for permissions dialog rendering, to avoid needing backend ACL setup.

Docs:
- `docs/PHASE56_PERMISSION_SET_UX_PARITY_DEV_20260215.md`
- `docs/PHASE56_PERMISSION_SET_UX_PARITY_VERIFICATION_20260215.md`

## Day 4 (P0) - Audit: Filtered Explorer + Export Presets UX

Goal:
- Admin can filter audit logs by:
  - user, eventType, category, nodeId, time range
- Export with presets (24h/7d/30d) and stable filenames.

Verification:
- Playwright: filter + export (download).

Code touchpoints:
- `ecm-frontend/src/pages/AuditPage.tsx` (or existing admin audit page)
- `ecm-core` audit endpoints (verify paging + filters match UI)

Acceptance:
- Filters persist in URL (shareable).
- Export filenames are stable and include date range + filters summary.

Docs:
- `docs/PHASE57_AUDIT_FILTER_EXPORT_UX_DEV_20260216.md`
- `docs/PHASE57_AUDIT_FILTER_EXPORT_UX_VERIFICATION_20260216.md`

## Day 5 (P1) - Version: Paged History UX + Major-Only Toggle

Goal:
- UI supports paged history and “major-only” view (already available in API).

Implementation:
- Version history dialog: add paging controls + major-only toggle.

Verification:
- Playwright: check paging + major-only toggles change visible versions.

Code touchpoints:
- `ecm-frontend/src/components/dialogs/VersionHistoryDialog.tsx`
- `ecm-core` versions endpoint(s) (ensure paging params align)

Docs:
- `docs/PHASE58_VERSION_HISTORY_PAGING_UX_DEV_20260217.md`
- `docs/PHASE58_VERSION_HISTORY_PAGING_UX_VERIFICATION_20260217.md`

## Day 6 (P1) - Search: “Did You Mean” + Saved Search Convenience

Goal:
- Surface spellcheck/suggestions in Search/Advanced Search.
- Make it easy to save the current advanced criteria.

Verification:
- Playwright: search misspelling shows suggestion chip; saving creates saved search.

Code touchpoints:
- `ecm-frontend/src/pages/SearchPage.tsx`
- `ecm-frontend/src/components/search/AdvancedSearchDialog.tsx`
- Search API spellcheck endpoint/response contract (ensure UI supports empty states)

Docs:
- `docs/PHASE59_SEARCH_SUGGESTIONS_SAVED_SEARCH_UX_DEV_20260218.md`
- `docs/PHASE59_SEARCH_SUGGESTIONS_SAVED_SEARCH_UX_VERIFICATION_20260218.md`

## Day 7 (Ops) - Regression Gate Rollup + Docs Index Refresh

Goal:
- One command runs the highest-signal E2E suite for Phase 5 admin tooling.

Implementation:
- Add `scripts/phase5-regression.sh` (or extend an existing smoke script).
- Update `docs/DOCS_INDEX_20260212.md` with Phase 5 entries.

Verification:
- `bash scripts/phase5-regression.sh` passes on a clean environment.

Regression gate contents (minimum):
- `ecm-frontend/e2e/ui-smoke.spec.ts`
- `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`
- `ecm-frontend/e2e/mail-automation.spec.ts` (if stable in this environment)

Docs:
- `docs/PHASE5_REGRESSION_GATE_ROLLUP_20260219.md`
