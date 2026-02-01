# 7-Day Development Plan (Dev)

Date: 2026-02-01

## Goal
Enhance upload clarity and preview status visibility, and keep UI behavior consistent with auto-hide rules.

## 7-Day Plan & Execution
Day 1 — Advanced Search preview status badges
- Added preview status chips + failure tooltip to Advanced Search results.
- Doc: `docs/PHASE3_P1_ADVANCED_SEARCH_PREVIEW_STATUS_DEV.md`

Day 2 — Upload completion admin action
- Added admin-only "System status" action in upload completion banner.
- Doc: `docs/PHASE3_P1_UPLOAD_COMPLETION_ADMIN_ACTION_DEV.md`

Day 3 — Upload completion summary (baseline)
- Completion banner shows counts and "Open folder" action.
- Doc: `docs/PHASE3_P1_UPLOAD_POSTPROCESSING_ACTIONS_DEV.md`

Day 4 — Upload error messaging
- Per-file failure reasons surfaced from backend errors.
- Doc: `docs/PHASE3_P1_UPLOAD_ERROR_MESSAGING_DEV.md`

Day 5 — Post-processing guidance
- Upload dialog hint for indexing/preview delays.
- Doc: `docs/PHASE3_P1_UPLOAD_POSTPROCESSING_HINT_DEV.md`

Day 6 — Preview status badges in list/grid/search
- Status chips in FileList and SearchResults.
- Doc: `docs/PHASE3_P1_PREVIEW_STATUS_BADGES_DEV.md`

Day 7 — Sidebar auto-hide consistency
- Sidebar now collapses when entering folders from main content areas.
- Doc: `docs/PHASE2_P1_SIDEBAR_AUTOHIDE_DEV.md`

## Primary Files Touched
- `ecm-frontend/src/pages/AdvancedSearchPage.tsx`
- `ecm-frontend/src/components/dialogs/UploadDialog.tsx`
- `ecm-frontend/src/components/browser/FileList.tsx`
- `ecm-frontend/src/pages/SearchResults.tsx`
- `ecm-frontend/src/pages/FileBrowser.tsx`
