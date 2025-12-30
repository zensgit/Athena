# Execution Plan (2025-12-28)

## Objective
Convert the requirements set into a staged execution plan with clear priorities and verification.

## Inputs
- `docs/REQUEST_INDEX_20251228.md`
- Related requirement documents listed there.

## Milestones & Priority

### Phase 0 (P0) — Auth/Router Stability + Search/Preview Correctness
**Scope**
- Fix login redirect stability and Keycloak token failure handling.
- Ensure API base URL and Keycloak URL are configurable in all envs.
- Correct search result actions (file → preview, folder → list).
- Eliminate `/api/v1/v1` path mistakes and missing assets (manifest/favicon).

**Deliverables**
- Stable login flow with explicit error handling.
- Consistent preview action across list/search views.

**Verification**
- Login flow does not loop; invalid token shows error.
- Search result opens correct target.
- UI loads without 404s for manifest/favicon.

---

### Phase 1 (P0) — PDF View/Annotate UX
**Scope**
- Clarify and implement PDF action visibility (View/Annotate vs Edit Online).
- Ensure PDF preview uses full viewport height (reduce bottom whitespace).
- Annotation mode toggle clearly visible and exits cleanly.

**Deliverables**
- PDF action policy documented and implemented.
- Preview layout tightened.

**Verification**
- PDF opens with correct controls; annotation mode works.
- No “Preview not available” for healthy pipeline.

---

### Phase 2 (P1) — UI Layout Polish
**Scope**
- Reduce excessive side gaps; improve grid/list spacing.
- Long filenames wrap to 2–3 lines with tooltip.
- Sidebar resize + auto-hide UX consistent.
- Breadcrumb duplication removed.

**Deliverables**
- UI polish changes for list/grid and navigation.

**Verification**
- Visual review on 1440px+ width.
- Hover shows full filename.

---

### Phase 3 (P1) — Upload & Processing Pipeline
**Scope**
- Upload progress and retry UX.
- Clear error messaging for size/type/virus.
- Show conversion state; provide fallback download.

**Deliverables**
- Upload UI improvements + backend status surfaced.

**Verification**
- Large file upload shows progress.
- Virus rejection shows clear toast and audit entry.

---

### Phase 4 (P1) — Security/Audit/Versioning
**Scope**
- Role-based action visibility in UI.
- Audit events for upload/download/delete/share/annotate/edit.
- Version history for annotations/edits.

**Deliverables**
- Role matrix enforcement in UI.
- Audit export includes required events.

**Verification**
- RBAC UI tests + audit export checks.

---

### Phase 5 (P2) — Sharing, Metadata, Tags/Categories
**Scope**
- Share link management with expiry/revoke and access logs.
- Metadata panel quick edit and batch tagging.
- Category tree usability improvements (optional drag/drop).

**Deliverables**
- Share management UI with audit logging.
- Faster metadata/tag workflows.

**Verification**
- Share link lifecycle covered in UI tests.
- Batch tag change reflected in facets.

---

## Execution Notes
- Each phase should include tests (UI or API) and a short verification report in `docs/`.
- Reuse `scripts/smoke.sh` and Playwright E2E where applicable.
- Avoid breaking changes to public APIs without explicit confirmation.

## Suggested Order (High to Low)
1. Phase 0: Auth/Router + Search/Preview
2. Phase 1: PDF View/Annotate UX
3. Phase 2: UI Layout Polish
4. Phase 3: Upload Pipeline
5. Phase 4: Security/Audit/Versioning
6. Phase 5: Sharing + Metadata
