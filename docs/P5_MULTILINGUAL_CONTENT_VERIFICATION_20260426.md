# Multilingual Content — Gap #14 Full Closeout (design + verification)

## Date
2026-04-26

## Status
Full-stack implementation complete (BE + FE). Closes Gap #14.

---

## Scope

Provide per-locale title and description overrides for any content node,
with Accept-Language-based resolution and an admin UI for managing translations.

---

## Files added / modified

| File | Change |
|------|--------|
| `ecm-core/src/main/resources/db/changelog/changes/088-create-localized-content.xml` | new — migration |
| `ecm-core/src/main/java/com/ecm/core/entity/LocalizedContent.java` | new — entity |
| `ecm-core/src/main/java/com/ecm/core/repository/LocalizedContentRepository.java` | new — repository |
| `ecm-core/src/main/java/com/ecm/core/service/LocalizedContentService.java` | new — service |
| `ecm-core/src/main/java/com/ecm/core/controller/LocalizedContentController.java` | new — controller |
| `ecm-core/src/main/resources/db/changelog/db.changelog-master.xml` | + 088 registration |
| `ecm-frontend/src/services/localizedContentService.ts` | new — typed API client |
| `ecm-frontend/src/pages/LocalizedContentPage.tsx` | new — admin page |
| `ecm-frontend/src/App.tsx` | + route `/admin/localized-content` (ROLE_ADMIN) |
| `ecm-frontend/src/components/layout/MainLayout.tsx` | + nav entry "Multilingual" |

---

## Migration 088 — `localized_content`

| Column | Type | Notes |
|--------|------|-------|
| `id` | UUID PK | gen_random_uuid() |
| `node_id` | UUID NOT NULL FK → nodes(id) | ON DELETE CASCADE |
| `locale` | varchar(20) NOT NULL | e.g. "en", "zh", "zh-CN" |
| `title` | varchar(500) | nullable |
| `description` | text | nullable |
| `created_date` | timestamp NOT NULL | default now() |
| `created_by` | varchar(150) | |
| `last_modified_date` | timestamp | JPA managed |
| `last_modified_by` | varchar(150) | JPA managed |
| `version` | bigint NOT NULL DEFAULT 0 | optimistic lock |
| `is_deleted` | boolean NOT NULL DEFAULT false | BaseEntity |
| `deleted_at` | timestamp | |
| `deleted_by` | varchar(150) | |

Constraints: `UNIQUE (node_id, locale)`, index on `node_id`.

---

## Entity — `LocalizedContent extends BaseEntity`

```java
@ManyToOne(fetch = LAZY) @JoinColumn(name = "node_id") Node node;
@Column(nullable = false, length = 20) String locale;
@Column(length = 500) String title;
@Column(columnDefinition = "TEXT") String description;
```

---

## Service — `LocalizedContentService`

### DTOs
```java
record LocalizedContentDto(UUID id, UUID nodeId, String locale,
    String title, String description,
    LocalDateTime createdDate, String createdBy, LocalDateTime lastModifiedDate) {}

record LocalizedContentRequest(String locale, String title, String description) {}
```

### `listForNode(UUID nodeId)` → `List<LocalizedContentDto>`
Returns all localizations ordered by locale ascending.

### `upsert(UUID nodeId, String locale, LocalizedContentRequest)` → `LocalizedContentDto`
- Normalizes locale: `trim().toLowerCase(Locale.ROOT)`
- Loads Node or throws `ResourceNotFoundException`
- Creates new or updates existing `(nodeId, locale)` pair

### `delete(UUID nodeId, String locale)`
- Existence-guarded; no-op if locale not found

### `resolve(UUID nodeId, String acceptLanguage)` → `Optional<LocalizedContentDto>`
Locale fallback algorithm:
1. Parse `Accept-Language` header: split on `,`, strip q-values (`; q=...`), trim each tag
2. For each tag: try **exact match** → try **language-only** (strip region, "zh-CN" → "zh")
3. If nothing matched: return **first DB entry** (default fallback)
4. Return `Optional.empty()` if no localizations exist for the node

---

## Controller — `LocalizedContentController`

All endpoints `@PreAuthorize("isAuthenticated()")`. `@Tag(name = "Multilingual Content")`.

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/nodes/{nodeId}/localizations` | List all locales for a node |
| PUT | `/api/v1/nodes/{nodeId}/localizations/{locale}` | Upsert locale (path locale is authoritative) |
| DELETE | `/api/v1/nodes/{nodeId}/localizations/{locale}` | Delete locale |
| GET | `/api/v1/nodes/{nodeId}/localization` | Resolve best locale via `Accept-Language` header |

### Security rationale
No additional manager/admin guard: localization access is scoped to nodes the caller can already read/write (same as `NodeController` pattern). Business-level node access is enforced upstream.

---

## API client — `localizedContentService.ts`

Class-instance singleton. Four methods matching the four endpoints.
`resolveLocalization` explicitly forwards `navigator.languages.join(',')` as the `Accept-Language` header (axios does not do this automatically).

---

## Page design — `LocalizedContentPage.tsx`

Route: `/admin/localized-content`

**Node lookup section:**
- "Node ID (UUID)" text field + "Load" button
- Calls `listLocalizations(nodeId)` on submit; shows error Alert on failure

**Localizations table (shown after node loaded):**
- Columns: Locale, Title, Description (truncated), Created By, Last Modified, Actions
- "Add Locale" button in table header
- Per-row: Edit button, Delete with inline confirmation (no dialog — row-level state toggle showing "Confirm? / Yes / No")

**AddLocaleDialog (embedded):**
- Locale: Select with 10 common options (en, zh, zh-CN, zh-TW, fr, de, es, ja, ko, ar) + "custom…" option that reveals a text field
- Existing locales show "(will overwrite)" hint
- Optional Title and Description fields

**EditLocaleDialog (embedded):**
- Locale shown as read-only chip (locale is the identity key, not editable)
- Title and Description editable; `useEffect` syncs fields when `dto` prop changes (handles re-open for different row)

State: local patch from API responses — no redundant refetch. `toast.success`/`toast.error` on every mutation.

---

## Routing and navigation

Route: `/admin/localized-content` — `ROLE_ADMIN` required.
Nav: "Multilingual" entry after "Disposition Schedules", using `Translate` icon.

---

## Compilation / TypeScript verification

```bash
# Backend
cd /Users/chouhua/Downloads/Github/Athena && ./mvnw compile -pl ecm-core -q
# → (no output — clean compile)

# Frontend
cd ecm-frontend && npx tsc --noEmit 2>&1 | grep -v node_modules
# → (no output — zero source-file errors)
```

---

## Non-goals (deferred)

- Integration with FileBrowser node-detail panel (requires reading locale from server on node select; can be added as a "Translations" tab in node properties)
- `Accept-Language` propagation through API responses (`Content-Language` header)
- Translation import/export (CSV/XLIFF)
- UI locale switcher for the Athena frontend itself (separate from content localization)
