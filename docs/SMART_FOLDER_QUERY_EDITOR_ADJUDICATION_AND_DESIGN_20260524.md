# Smart-Folder Post-Create Query Editor (C′) — Adjudication & Implementation Brief (read-only)

Date: 2026-05-24
Status: **read-only brief — no code/test/schema/`.env` written by this document.**
Candidate: **C′** in `docs/PRODUCT_CAPABILITY_DISCOVERY_20260524.md` (refresh ranking #2).
Revision: **v2** (gate round 1 findings folded in).

## Revision history

- **v1 → v2** (gate round 1):
  - **Blocker 1** — folder-contents `NodeResponse` (`FolderController.java:385`) carries **no** `smart`/`queryCriteria`, and `getChildren` maps it via `apiNodeToNode` (`nodeService.ts:2488`) without those fields. So a `node.smart === true` context-menu gate is impossible from a browse-list row. **Fix:** detection + prefill both come from a folder-detail fetch (`GET /folders/{folderId}` → `FolderResponse`), not the list row.
  - **Blocker 2** — `nodeService.getNode(folderId)` hits `/nodes/{id}` → `NodeDto` (`NodeDto.java:18`), which has no `smart`/`queryCriteria` (frontend `ApiNodeDetailsResponse`/mapper at `nodeService.ts:71,2327` likewise). **Fix:** add `nodeService.getFolder(folderId)` calling the existing `GET /folders/{folderId}` (`FolderController.java:62`) → `FolderResponse` (which **does** carry `smart` + `queryCriteria`), and use it for both detection and prefill.
  - **Finding 3** — gate on the **`canWrite` gate used by delete/write actions** (`FileList.tsx:1504`); Rename is currently always shown (`:1296`), so "mirror rename/delete" was imprecise.

## 0. Purpose

A smart folder's search criteria can be set at **creation** (`CreateFolderDialog`) but never edited afterward — refining the query today means deleting and re-creating the folder (losing its identity, path, and permissions). This slice adds an **Edit Smart Folder** flow that updates an existing smart folder's `queryCriteria` in place.

This is a **frontend-only** slice: the backend update path already exists and validates. The discovery's "Refresh contents" half is explicitly dropped — smart folders are **live-on-read** (`FolderService.getSmartFolderContents:500` → `searchService.search:519` on every fetch), so a refresh affordance is redundant.

## 1. Adjudication — memory / closeout cross-check

- Folders are not the RM-preset surface; no `project_rm_preset_delivery_closeout` collision. No other closeout zone touches `FolderController`/`FolderService`/`CreateFolderDialog`.
- The deliverable is frontend (`*.tsx` + service) — **not** a test-only surface, so it clears the discovery exclusion list.

**Verdict:** in-scope, frontend-only, no prohibition. Proceed.

## 2. The gap (verified in primary source)

**Backend update path already exists and supports editing smart-folder criteria:**
- `FolderController.java:117` `PUT {/api/folders, /api/v1/folders}/{folderId}` → `UpdateFolderRequestDto` carrying `isSmart` + `queryCriteria` (`:315-325`).
- `FolderService.updateFolder:268` — checks `WRITE` permission (`:272`, else `SecurityException`), lock (`:276`, else `IllegalStateException`), then `applySmartFolderSettings:550`.
- `applySmartFolderSettings`: if `targetSmart` (provided `isSmart`, else current) is true, normalizes the criteria (provided, else existing), runs `validateSmartFolderQueryCriteria:582`, and saves. Converting a **non-empty** non-smart folder to smart is rejected (`:574-576`) — not relevant when editing an already-smart folder.
- `validateSmartFolderQueryCriteria`: criteria must be non-empty, convertible to a `FacetedSearchRequest`, and define at least one of `query` / `filters` / `pathPrefix` (`:597-603`).
- `FolderResponse` already exposes `smart` + `queryCriteria` (`:351-352`).

**No frontend path reaches it, and the browse list cannot even detect smart-ness:**
- `nodeService.createFolder` POSTs `/folders` (create only); `nodeService.updateNode` PATCHes `/nodes/{id}` (generic node update — does **not** carry `isSmart`/`queryCriteria` through `applySmartFolderSettings`). There is **no** `updateFolder` (PUT `/folders/{id}`) method.
- **Browse-list rows lack `smart`/`queryCriteria`** — folder-contents `NodeResponse` (`FolderController.java:385`) omits both, and `getChildren` → `apiNodeToNode` (`nodeService.ts:2488`) does not map them. **`GET /nodes/{id}` → `NodeDto` also omits both** (`NodeDto.java:18`; frontend mapper `nodeService.ts:71,2327`). The **only** shape carrying `smart` + `queryCriteria` is `FolderResponse`, returned by `GET /folders/{folderId}` (`FolderController.java:62`) and `POST /folders`.
- `CreateFolderDialog.tsx` is create-only; `queryCriteria` appears only there + services/types.
- `FileList.tsx` shows folders with a per-node context menu (rename always shown `:1296`; delete `canWrite`-gated `:1504`; move/copy/preview) but no edit-smart-folder action.

## 3. Scope (locked)

Add a frontend **Edit Smart Folder** flow:
1. `nodeService.getFolder(folderId)` → `api.get('/folders/{folderId}')` → `FolderResponse`, reusing the **existing** FolderResponse assert/normalize (the one `createFolder`/`getRootFolder` use). Supplies `smart` + `queryCriteria` for both detection and prefill.
2. `nodeService.updateFolder(folderId, request)` → `api.put('/folders/{folderId}', body)`, returning the updated folder via that same FolderResponse guard (no new sentinel/normalizer).
3. An **EditSmartFolderDialog** mirroring `CreateFolderDialog`'s smart fields — **search query + path prefix only** (parity; see OOS) — seeded from the fetched folder's current criteria.
4. An **"Edit Smart Folder"** entry in `FileList.tsx`'s context menu for a writable folder; the click-time `getFolder` fetch resolves smart-ness (open if smart, else informative toast) and prefill in one call (see §5 visibility).

## 4. Out of scope — explicit no-touch ring

1. **No backend change** — `FolderController` / `FolderService` / DTOs / `applySmartFolderSettings` / validation are reused as-is. No new endpoint, no Liquibase.
2. **"Refresh contents"** — non-gap (live-on-read).
3. **Filters editor** — `CreateFolderDialog` does not expose `filters` (only `query` + `pathPrefix`); keep parity and defer a filters UI.
4. **smart↔non-smart conversion** — this dialog edits an already-smart folder's criteria; it does not toggle `smart` off, nor convert a plain folder (backend rejects non-empty conversion anyway).
5. **Rename / description** — separate concerns handled by existing rename; not bundled here.
6. **`CreateFolderDialog` refactor** to extract a shared smart-fields component — duplicate the small form for this slice; extraction is a fast-follow.
7. **`SavedSearchesPage`** — its "create smart folder from a saved search" flow is a separate create path; untouched.

## 5. Contract

### Prefill — fetch via `getFolder` (the only valid source)
On "Edit Smart Folder" click, call the new `nodeService.getFolder(folderId)` (`GET /folders/{folderId}` → `FolderResponse`) to read the **current** `smart` + `queryCriteria` fresh, then seed the dialog from that response. `getNode`/`/nodes/{id}` (`NodeDto`) and the browse-list `NodeResponse` **do not carry these fields** (Blockers 1 & 2), so `getFolder` is not just freshest — it is the only shape that has the data. The fetch is cheap and also resolves the freshness concern (edit-after-edit, or another session's edit).

### Request — self-describing PUT body
```
PUT /api/v1/folders/{folderId}
{
  "isSmart": true,                 // sent EXPLICITLY — do not rely on the server's
                                   //   "both null -> no-op / infer from current" branch;
                                   //   self-describing request matches the create-flow shape
  "queryCriteria": {
    "query": "<non-blank>",        // form field name `searchQuery` -> payload key `query`
    "pathPrefix": "<optional>"     // omit when blank (mirror CreateFolderDialog)
  }
}
```
Other `UpdateFolderRequestDto` fields (name/description/folderType/…) are omitted (null → untouched by `updateFolder`).

### Response
`FolderResponse` (`smart`, `queryCriteria`, …). On success: toast, close, and refresh the browser listing so the row reflects the new criteria (contents are live-on-read, so the listing re-evaluates on next fetch).

### Field-name mapping (lock — must match create dialog)
Form uses `searchQuery` (RHF field name, as in `CreateFolderDialog`); payload key is `query`. Keeping the same mapping prevents the two forms drifting.

### Validation / error mapping (existing `RestExceptionHandler`)
- Empty criteria / no `query|filters|pathPrefix` / `"queryCriteria is only supported for smart folders"` → `IllegalArgumentException` → **400**. Dialog gates submit on a non-blank query client-side (mirror create dialog's "Search query is required") so 400s are rare.
- No `WRITE` permission → `SecurityException` → **403**. Dialog must **not** crash — toast and stay open.
- Folder locked by another user → `IllegalStateException` (`FolderService:276-278`) → **500** via the handler. Toast a clear "folder is locked" by matching the message substring, **or** accept the generic error toast and document the limitation (decision D-lock below).

### Context-menu visibility (`FileList.tsx`)
Browse-list rows lack `smart` (Blocker 1), so the item **cannot** be conditionally rendered on `node.smart`. Design:
- Show "Edit Smart Folder" when `contextMenu.node` is a **folder** AND the user **can write** — reuse the existing `canWrite` gate that guards delete/write actions (`FileList.tsx:1504`); **not** the rename item, which is always shown (`:1296`). Never on documents.
- On click, `getFolder(folderId)`: if `smart === true`, open the dialog seeded from `queryCriteria`; if not smart, `toast.info("This folder is not a smart folder")` and do nothing (no conversion — OOS). This single click-time fetch serves both detection and prefill.
- Rationale for click-time (vs. prefetching on menu-open to hide the item on plain folders): a menu-open prefetch adds async flicker and a fetch per right-click; the click-time branch is simpler, frontend-only, and resolves gracefully. The only cost is the item showing on plain writable folders, handled by the toast. (Conditional render via menu-open enrichment is a possible refinement; not recommended for v1.)

## 6. Affected surfaces

- **Frontend only:**
  - `services/nodeService.ts` — new `getFolder(folderId)` (`GET /folders/{folderId}` → `FolderResponse`) **and** `updateFolder(folderId, request)` (PUT), both reusing the existing FolderResponse assert/normalize used by `createFolder`/`getRootFolder` (do not add a new guard). Note: `nodeService` currently avoids `GET /folders/{id}` for the generic node path (comment at `:2387`); this adds a dedicated folder fetcher for the smart-folder case, which is the only shape carrying `queryCriteria`.
  - `components/dialogs/EditSmartFolderDialog.tsx` (new) — RHF form with `searchQuery` (required) + `pathPrefix` (optional), seeded from the fetched folder; submit gated on non-blank query; success/permission/lock toasts.
  - `components/browser/FileList.tsx` — context-menu item (folder + `canWrite`, per §5) that on click fetches via `getFolder`, branches on `smart`, opens the dialog, and on success refreshes the listing.
  - `types` — reuse existing `Node.smart`/`Node.queryCriteria`; add a request type if helpful.
- **Backend:** none. **Schema:** none.

## 7. Decisions (gate-adjudicated round 1)

- **D-lock — RESOLVED:** locked-folder `IllegalStateException`→500 is **substring-mapped** to a clear "folder is locked" toast in the dialog's error handling (rather than the generic error toast).
- **D-entry — RESOLVED:** `FileList.tsx` context menu is the home, with the **click-time `getFolder` fetch/enrichment** (§5) — it cannot gate on a list-row `smart` flag (Blocker 1).
- **D-fields — RESOLVED:** **query + pathPrefix only**, consistent with the create dialog (filters OOS).

## 8. Reused patterns / sizing

- Dialog mirrors `CreateFolderDialog` (RHF, toast, validation copy). Service method mirrors `createFolder` (same response guard). HTTP-success-is-semantic: PUT returns the updated folder → refresh the listing (`feedback_http_success_is_not_semantic_success`).
- Phase 5 Mocked: PUT `/folders/{id}` is an existing endpoint; route through the existing folder-response normalizer (do not invent one). HTML-fallback risk is low but the existing guard covers it.
- **Estimated size:** Small (< 1 pd). Dialog + service method + one context-menu item + tests.
- **Risk:** Low. No backend/schema change; worst case is a rejected edit (validation/permission/lock) surfaced as a toast. Live-on-read means the edited folder reflects new criteria on next read with no extra refresh wiring beyond re-listing.

## 9. Memory checklist applied

- `feedback_brief_paths_must_be_grep_verified` — every path/endpoint above grep-verified: GET folder `FolderController:62`, PUT `:117`, DTO `:315`, folder-contents `NodeResponse:385` (no smart), service `updateFolder:268`, validate `:582`, `FolderResponse:339` (has smart+queryCriteria), `NodeDto:18` (no smart), `FileList` delete-gate `:1504` / rename `:1296`.
- `feedback_http_success_is_not_semantic_success` — success path refreshes the listing from the returned folder.
- `feedback_guard_predicates_real_backend_shape_drift` — reuse the existing folder-response guard rather than a new strict one.
- `feedback_per_slice_fix_commit_stages_code_and_test` — co-locate the new dialog test; stage code + test together at implementation.

## 10. What this brief does not commit to

- No track opened; no code/test/frontend/schema/`.env` change; no commits by the brief itself.
- §5 contract is firm; §7 decisions are gate-resolved (round 1). v2 is ready for implementation pending gate sign-off.
- Implementation begins only after gate adjudication, per the established discovery → brief → gate → implement cadence.

## 11. Verification (this brief)

```bash
git status --short                                  # M .env + this brief only
git diff --stat -- 'ecm-core/' 'ecm-frontend/'      # empty
```
