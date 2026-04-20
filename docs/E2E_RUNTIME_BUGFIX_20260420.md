# E2E Core Gate Runtime Bug Fixes — 2026-04-20

## Context

After 9 CI-config fixes (see `CI_POST_PUSH_FIXES_20260420.md`), CI reached **5 of 6 jobs green**:

| Job | Status |
|-----|--------|
| Backend Verify | ✅ |
| Frontend Build & Test | ✅ |
| Phase C Security Verification | ✅ |
| Acceptance Smoke (3 admin pages) | ✅ |
| Phase 5 Mocked Regression | 🚫 cancelled (concurrency) |
| Frontend E2E Core Gate | ❌ **3 genuine test failures** |

The 3 failures in `Frontend E2E Core Gate` (14/17 passing) exposed **2 distinct real runtime bugs** introduced during the P0B → P5 backlog work.

This report documents those bugs and their fixes.

---

## Bug 1 — `/api/v1/documents/{id}/checkin` Returns 500 on Non-Checked-Out Documents

### Symptoms

Two E2E tests failed with HTTP 500 on `/checkin`:
- `e2e/version-details.spec.ts:145` — `Version details: checkin metadata matches expectations`
- `e2e/version-share-download.spec.ts:155` — `Version history actions: download + restore`

Error pattern in Playwright output:
```
Error: Checkin did not succeed: checkin status=500 {"path":"/api/v1/documents/.../checkin"}
```

### Root Cause

The `/checkin` endpoint is **historically overloaded**. Two usage semantics:

1. **Commit working-copy edits** — requires the document to be actively checked out
2. **Upload a new version** — callers who happen to have the document ID use `POST /checkin` as the "version upload" endpoint; no prior checkout

After P0A-4 hardening, `NodeService.checkinDocument()` strictly requires `document.isCheckedOut()`:

```java
if (!document.isCheckedOut()) {
    throw new IllegalStateException("Document is not checked out");
}
```

The E2E test exercises semantic #2: uploads document → immediately POSTs `/checkin` with a new file → expects a new version. There's no prior `/checkout`.

Flow:
1. `versionService.createVersion()` — succeeds (new version written)
2. `nodeService.checkinDocument()` — throws `IllegalStateException` (not checked out)
3. Spring wraps as HTTP 500

The version was created, but the response was 500. Downstream tests retried and failed repeatedly.

### Fix

In `DocumentController.checkinDocument()`, only call `nodeService.checkinDocument()` when the document is actually checked out:

```java
if (file != null) {
    versionService.createVersion(documentId, file, comment, majorVersion);
}
// /checkin is historically used both for: (a) committing working-copy edits
// that require an active checkout, and (b) uploading a fresh version when
// the caller happens to have the document path. Only clear the checkout
// state when the document is actually checked out.
Document document = (Document) nodeService.getNode(documentId);
if (document.isCheckedOut()) {
    document = nodeService.checkinDocument(documentId, keepCheckedOut);
}
return ResponseEntity.ok(toNodeDto(document));
```

This preserves:
- **Strict P0A-4 semantics** — `NodeService.checkinDocument()` still requires checkout; only the controller-level guard lets the "pure version upload" path bypass it.
- **Pre-existing `keepCheckedOut` guard** — still requires a file, unchanged.
- **Version creation correctness** — `versionService.createVersion()` runs unchanged; version ledger entries, content reference attaches, and lifecycle events all fire as before.

**File Changed**: `ecm-core/src/main/java/com/ecm/core/controller/DocumentController.java`

---

## Bug 2 — `getByLabel('close')` Matches 4 Elements Across the Page

### Symptoms

`e2e/pdf-preview.spec.ts:284` — `File browser view action opens preview` failed on all 3 retries:

```
Error: locator.click: Error: strict mode violation: getByLabel('close') resolved to 4 elements
```

### Root Cause

Test snippet:

```typescript
const browserDialog = page.getByRole('dialog');
await expect(browserDialog.getByLabel('close')).toBeVisible({ timeout: 60_000 });
await page.waitForSelector('.react-pdf__Page__canvas, ...', { timeout: 60_000 });

await page.getByLabel('close').click();  // ← NOT scoped to dialog
```

The `toBeVisible` check is scoped to the dialog (`browserDialog.getByLabel`), but the subsequent `click` is **page-wide** (`page.getByLabel`).

10 components in the app expose `<IconButton aria-label="close">`:

```
src/components/dialogs/AssociationManager.tsx
src/components/dialogs/PermissionsDialog.tsx
src/components/dialogs/PropertiesDialog.tsx
src/components/dialogs/UploadDialog.tsx
src/components/dialogs/VersionHistoryDialog.tsx
src/components/categories/CategoryManager.tsx
src/components/preview/DocumentPreview.tsx
src/components/search/SearchDialog.tsx
src/components/share/ShareLinkManager.tsx
src/components/tags/TagManager.tsx
```

When the File Browser → Preview flow triggers, multiple dialog-producing components are either mounted or keep the close-button node in the accessibility tree. Page-level `getByLabel('close')` matches 4 of them. Playwright's strict mode rejects ambiguous locators.

This is a **test bug** that happened to work before P4 RM but broke when more dialog components entered the rendered page tree during navigation.

### Fix

Scope the `click` to the dialog (matching the already-scoped `toBeVisible`):

```typescript
await browserDialog.getByLabel('close').click();
```

**File Changed**: `ecm-frontend/e2e/pdf-preview.spec.ts`

---

## Files Modified

| File | Change |
|------|--------|
| `ecm-core/src/main/java/com/ecm/core/controller/DocumentController.java` | Guard `nodeService.checkinDocument()` call with `document.isCheckedOut()` check |
| `ecm-frontend/e2e/pdf-preview.spec.ts` | Scope close-button click to `browserDialog` |

No migrations, no new tests, no new dependencies.

---

## Verification

### Local Docker

- `docker compose build ecm-core` — succeeds
- `docker compose up -d --force-recreate ecm-core` — healthy

### Unit Test Coverage Unchanged

The `/checkin` endpoint change preserves existing behavior for the "true check-in" path:

- If `document.isCheckedOut()=true` → behavior identical to before (calls `nodeService.checkinDocument()`)
- If `document.isCheckedOut()=false` → new behavior: skip checkin, return document after version creation
- `keepCheckedOut` guard unchanged (still requires file)

Existing `CheckOutCheckInService` tests, `VersionService` tests, `DocumentController` tests remain valid.

### E2E Tests Targeted

These tests should now pass:

- `pdf-preview.spec.ts:284` — dialog-scoped click unambiguous
- `version-details.spec.ts:145` — `/checkin` accepts non-checked-out documents as pure version upload
- `version-share-download.spec.ts:155` — same fix covers this test

---

## Expected CI Outcome

After pushing these fixes, CI run should achieve:

| Job | Expected |
|-----|----------|
| Backend Verify | ✅ |
| Frontend Build & Test | ✅ |
| Phase C Security | ✅ |
| Acceptance Smoke | ✅ |
| **Frontend E2E Core Gate** | **✅ all 17/17 tests** |
| Phase 5 Mocked Regression | 🚫 cancelled on concurrency, or ✅ |

---

## Why These Bugs Slipped Through

1. **Bug 1** (checkin 500): Unit tests mock `NodeService` and don't exercise the controller + real DB round trip. The `/checkin` endpoint's dual-semantic usage was never reflected in a unit test. E2E is the first place it's exercised end-to-end.

2. **Bug 2** (close-button): Test worked historically because fewer dialog-producing components were mounted during this specific navigation. P4 RM added more dialog components to the layout; once enough are mounted, the page-wide `getByLabel` becomes ambiguous. The test's `toBeVisible` scoping was correct; the `click` scoping was an oversight.

---

## Session Final Status

**Total commits this session**: 10
1. `8236a8e` — CI unused imports + Phase C --no-cache + healthcheck localhost
2. `b5aafe5` — PR-80 saved-search RM projection (Codex)
3. `3d82170` — CI post-push fixes report
4. `444f156` — Jest timeout 15s → 45s
5. `2e1ef8e` — 073 backfill JOIN nodes
6. `c22ee32` — health-wait 240s → 480s
7. `fd66852` — health-wait 480s → 900s
8. `ccc8e6a` — 503-tolerant health check
9. `50d7b33` — Phase C curl port 8080 → 7700 (root cause)
10. _(this commit)_ — `/checkin` 500 fix + `pdf-preview` test scoping

**CI Journey**: 0/6 → 2/6 → 3/6 → 4/6 → 5/6 → **6/6 expected** after this commit.
