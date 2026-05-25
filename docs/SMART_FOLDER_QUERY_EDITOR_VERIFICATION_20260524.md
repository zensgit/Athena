# Smart-Folder Post-Create Query Editor (C′) — Verification

Date: 2026-05-24
Brief: `docs/SMART_FOLDER_QUERY_EDITOR_ADJUDICATION_AND_DESIGN_20260524.md` (v2, gate-approved).

## Production changes (frontend only — no backend/schema)

- `services/nodeService.ts`
  - `getFolder(folderId)` — `GET /folders/{folderId}` → `FolderResponse` via the existing `assertAndNormalizeFolderResponse` + `folderToNode` (which already maps `smart` + `queryCriteria`). The only shape carrying smart-folder criteria; source for both detection and prefill.
  - `updateFolder(folderId, {name?, description?, isSmart?, queryCriteria?})` — `PUT /folders/{folderId}` → `FolderResponse`, same guard. The edit dialog sends `isSmart: true` + `queryCriteria` explicitly (self-describing).
- `components/dialogs/EditSmartFolderDialog.tsx` (new) — controlled dialog (`open`/`onClose`/`folder`/`onUpdated`), RHF form seeded from the fetched folder's `queryCriteria`: `searchQuery` (required, form-name → payload key `query`, matching the create dialog) + `pathPrefix` (optional, omitted when blank). Submit gated on non-blank query. Success → `toast.success` + `onUpdated` + `onClose`.
- `components/browser/FileList.tsx` — context-menu item **"Edit Smart Folder"**, shown for `nodeType === 'FOLDER'` AND `canWrite` (mirrors the delete gate, `:1504`; not rename which is always shown). On click → `nodeService.getFolder(id)`; if `smart` open the dialog seeded from it, else `toast.info('This folder is not a smart folder')`. `onUpdated` → `refreshCurrentFolder()`.

## Tests added

- `EditSmartFolderDialog.test.tsx` (5) — prefill from `queryCriteria`; submit disabled on cleared query; success path PUTs `{isSmart:true, queryCriteria:{query, pathPrefix}}` + toasts + closes; empty path prefix omitted; **server error stays open with NO double-toast** (see D-lock divergence below).
- `nodeService.folderNodeCrud.test.ts` (+3) — `getFolder` maps smart+queryCriteria and locks `GET /folders/{id}`; HTML fallback → sentinel; `updateFolder` locks the `PUT /folders/{id}` body and maps the response. Added `put` to the api mock.

## Local verification

```
EditSmartFolderDialog.test.tsx ........... 5/5 PASS
nodeService.folderNodeCrud.test.ts ....... PASS (incl. 3 new getFolder/updateFolder tests)
react-scripts build (CI=true) ............ success (ESLint clean — CI=true fails on warnings)
```

(27 tests across the two suites green; no FileList test exists to regress.)

## Implementation diffs from brief / decisions

1. **D-lock — divergence with evidence.** The brief (gate D-lock) called for the dialog to substring-map the locked-folder error to a clear toast. Primary-source check of `services/api.ts:141-150` shows the **response interceptor already unconditionally `toast.error(error.response.data.message)`** for any non-401 error carrying a message — so the backend's lock text ("Folder is locked by …"), permission text, and validation text are all surfaced automatically. The dialog therefore does **not** re-toast on error (it only keeps itself open for retry); a dialog-level toast would double up. Net effect matches the gate's intent (operator sees a clear locked message) without the duplicate. Locked-in by the "no double-toast" dialog test.
2. **Icon reuse** — the context-menu item reuses the already-imported `Edit` icon (no new icon import).
3. **Refresh** — `onUpdated` calls the existing `refreshCurrentFolder()`. Strictly optional (the edited folder's contents are live-on-read and the parent row is unchanged), but harmless and consistent with other actions.
4. **Click-time fetch latency** — the menu closes immediately on click (acknowledging the action); a `smartFolderLoading` guard prevents a second concurrent `getFolder` if the operator re-opens the menu and clicks again before the first resolves. The brief's "click-time fetch" left latency unaddressed; this is the minimal guard. The 1–5s silent wait on a slow backend is an accepted rough edge (no spinner) — a fast-follow if it bites.
5. **Form-name lock** — the dialog test asserts the query input's `name` is `searchQuery` (not just the payload mapping), pinning parity with `CreateFolderDialog` so a field rename can't silently drift.

## CI Follow-Up

```
Run id:        26387975548
Head SHA:      66b6982e
Conclusion:    success (7/7 on first attempt — gh run view authority per feedback_gh_run_watch_unreliable)
URL:           https://github.com/zensgit/Athena/actions/runs/26387975548

Jobs (7/7 green):
  ✓ Backend Verify
  ✓ Frontend Build & Test
  ✓ Phase C Security Verification
  ✓ Acceptance Smoke (3 admin pages)
  ✓ Frontend E2E Core Gate
  ✓ Property Encryption Closeout Gate
  ✓ Phase 5 Mocked Regression Gate
```
