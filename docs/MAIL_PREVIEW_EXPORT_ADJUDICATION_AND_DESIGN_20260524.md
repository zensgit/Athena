# Mail Rule Preview → One-Shot Export to Folder — Adjudication & Implementation Brief (read-only)

Date: 2026-05-24
Status: **read-only brief — no code/test/schema/`.env` written by this document.**
Candidate: **B** in `docs/PRODUCT_CAPABILITY_DISCOVERY_20260524.md` (refresh ranking #1).

## 0. Purpose

Mail rule **preview** (`POST /api/v1/integration/mail/rules/{id}/preview`) lets an admin dry-run a rule and *see* which unseen messages would match — but the matched set is read-only diagnostic. Operators cannot turn that preview into anything durable. This brief scopes a **one-shot export to a folder** so a trial-run match set can be staged as documents for review **before** the operator commits to activating the rule.

The scope is deliberately cut to one-shot export only. Everything adjacent (scheduling, rule activation, ingestion rewrite, background workers) is **out of scope** — see §3.

## 1. Adjudication — memory / closeout cross-check

- **RM preset delivery closeout** (`project_rm_preset_delivery_closeout`, PR-95..121 closed 2026-04-23): RM-surface only. Mail automation is a structurally separate subsystem (`integration/mail/**`). No collision.
- **Mail report scheduled export already exists** — `integration/mail/service/MailReportScheduledExportService.java` and `GET /report/schedule`, `POST /report/schedule/run`. **This is a different thing**: it exports the mail *diagnostics/report* (CSV of fetch stats), NOT rule-matched *messages*. This brief does not touch it, and scheduling of any kind is OOS regardless (§3). Naming must stay distinct: this slice is "preview export", never "report export".
- No feedback memory flags the mail-export surface. The relevant **engineering** memories that DO apply are reuse patterns, not prohibitions: per-row partial success (bulk-declare / legal-hold), error-message sanitization, Phase 5 mocked HTML-fallback sentinel, contract nullability, HTTP-success-is-not-semantic-success. See §8.

**Verdict:** in-scope as a new capability on the mail surface; no closeout/memory prohibition. Proceed to brief.

## 2. The crux (verified in primary source)

**A preview match carries metadata only — no bytes.** `MailRulePreviewMessage(folder, uid, subject, from, recipients, receivedAt, attachmentCount, processable)` (`MailFetcherService.java:1159-1168`). The preview re-extracts body/attachments transiently for matching (`:482-510`) but stores none of it.

**Therefore export cannot replay the preview payload — it must re-connect to the mail server and re-fetch each `(folder, uid)`, then run the existing ingestion core.** The ingestion core is:

- `processContent(Message, MailRule, List<AttachmentPart>, Map mailProperties, UUID targetFolderId)` (`:2148-2170`) → honors `rule.getActionType()` (`METADATA_ONLY` / `ATTACHMENTS_ONLY` / `EVERYTHING`):
  - `ingestEmailMessage` (`:2172-2186`) → `emailIngestionService.ingestEmail(emlFile, folderId, mailProperties)`; assigns `rule.getAssignTagId()` if set.
  - `ingestAttachments` (`:2188-2225`) → `uploadService.uploadDocument(file, folderId, null)` per attachment, honoring `includeInlineAttachments` + `attachmentMatchesRule`.
  - **`targetFolderId` already overrides `rule.getAssignFolderId()`** in both helpers (`:2184`, `:2218`) — so an operator-chosen export folder is a first-class parameter the core already supports. No ingestion refactor needed.

`processContent` takes a **live `jakarta.mail.Message`**, so export must hold an open IMAP connection while it re-fetches and ingests — exactly what `previewRule` already does synchronously (`connect(account)` at `:451`, `store.close()` in `finally`). Export mirrors that connection lifecycle.

**Between preview and export the mailbox can drift** (message expunged, or the live rule already ingested it). Export must handle each row independently → **per-row partial success** (the established bulk-declare / legal-hold pattern). This is the natural orchestration shape.

## 3. Out of scope — explicit no-touch ring

The following are **OOS** and must not be touched or implied by the slice:

1. **Scheduled / recurring delivery** — no scheduler, no cron, no `*_export_job` table, no integration with `MailReportScheduledExportService`. One-shot, operator-initiated only.
2. **Rule activation** — export does not enable, disable, or alter the rule's active state, schedule, or `assignFolderId`. The rule is read for its `actionType` / `assignTagId` / attachment settings only.
3. **Mail ingestion refactor** — `processContent` / `ingestEmailMessage` / `ingestAttachments` / `emailIngestionService` / `uploadService` are reused **as-is**. No signature changes, no new ingestion modes.
4. **Background worker / async** — synchronous foreground, mirroring `previewRule`. No `@Async`, no job queue, no progress polling.
5. **Mailbox mutation** — see Decision D1; the default recommendation is non-destructive (no delete/move/flag, no SEEN).

Any of these resurfacing in implementation is a scope breach to flag back to the gate.

## 4. Decision points for the gate (must rule before code)

| # | Decision | Options | Brief's recommendation |
|---|---|---|---|
| **D1** | **Mailbox side-effects of export** | (a) pure non-destructive staging copy — open `READ_ONLY`, no `applyMailAction`, no mark-SEEN, no `recordProcessedMail`; (b) record processed; (c) apply the rule's post-action | **(a) non-destructive.** Export is a *trial stage for review*; it must not mutate the mailbox nor consume the message against a not-yet-activated rule. Consequence: re-export duplicates (D4). |
| **D2** | **Selection model** | (a) operator-selected subset of preview rows `[{folder, uid}]`; (b) export *all* matched | **(a) explicit `[{folder,uid}]` selections.** Lets the operator stage a curated subset; frontend adds per-row checkboxes. Empty selection → 400. |
| **D3** | **Target folder** | (a) required explicit operator choice; (b) default to `rule.getAssignFolderId()` | **(a) required explicit `targetFolderId`.** Defaulting to the rule's assign folder makes export == what the live rule does, defeating the "stage elsewhere for review" value. Reject null/blank with 400. |
| **D4** | **Already-ingested handling** | (a) honor `processedMailRepository.existsByAccountIdAndFolderAndUid` and return `SKIPPED_ALREADY_PROCESSED`; (b) ignore and re-ingest | **(a) honor the skip** (same guard preview uses at `:476`) — avoids duplicating what the live rule already imported. Re-running export itself can still duplicate (since D1 records nothing); document this caveat in UI copy. |
| **D5** | **Message gone between preview & export** | status vs error | **`SKIPPED_NOT_FOUND`** (status, `errorCategory == null`). Benign environmental drift, not a failure. |
| **D6** | **Selection size cap** | uncapped vs hard cap | **Hard cap** (reuse `resolveDebugMaxMessages` ceiling or a fixed cap e.g. 200) — synchronous IMAP re-fetch + N uploads must not run unbounded. Over-cap → 400. |
| **D7** | **`documentIds` in the per-row response** | (a) omit — call `processContent` as-is (returns `boolean` at `:2168`), status only; (b) include — orchestrator **inlines** the leaf calls `emailIngestionService.ingestEmail(...)` (returns `Document`) + `uploadService.uploadDocument(...)` (returns `getDocumentId()`) and captures IDs | **(a) omit for v1.** `processContent` only returns `boolean`; surfacing `documentIds` forces either modifying the ingestion helpers (= the OOS #3 ingestion refactor) or inlining a copy of the actionType-routing in the new orchestrator (option b). The tight-scope choice is to call `processContent` unchanged → `true`⇒`EXPORTED`, `false`⇒`SKIPPED_NO_CONTENT`, and let the operator open the target folder to see staged docs. Deep-linking via option (b) is a clean fast-follow if the gate wants it. **The §5 contract below assumes (a).** |

If the gate prefers different answers, the contract in §5/§6 shifts accordingly — flag before coding.

## 5. Endpoint contract (proposed)

**New endpoint** (mirrors the preview endpoint's home, path-var name, + auth):

```
POST /api/v1/integration/mail/rules/{id}/preview/export
@PreAuthorize("hasRole('ADMIN')")     # mirrors previewRule (MailAutomationController.java:893)
@PathVariable UUID id                  # literally "id" to match the sibling POST /rules/{id}/preview (:899)
```

**Request** (`MailRulePreviewExportRequest`):
```jsonc
{
  "accountId": "<uuid>",                 // required; must match the rule's accountId if rule.accountId != null (mirror :424-426)
  "targetFolderId": "<uuid>",            // required (D3) — non-null, must resolve to an existing folder
  "selections": [                        // required, non-empty (D2); capped (D6)
    { "folder": "INBOX", "uid": "12345" }
  ]
}
```

> **`comment` dropped.** It was contract bloat: there is no audit row, no export-job row (schema = none), and the message/mailbox is not mutated (D1), so a comment would have no destination. If the gate later wants export provenance, the right home is a document property on each staged doc — but that needs a property-collision check against the mail-ingestion properties and is deferred. Not in v1.

**Response** (`MailRulePreviewExportResult`) — per-row, partial-success (assumes D7 option (a), no `documentIds`):
```jsonc
{
  "accountId": "<uuid>",
  "ruleId": "<uuid>",                  // echoed from the path {id}
  "targetFolderId": "<uuid>",
  "exported": 3,
  "skipped": 1,
  "failed": 1,
  "rows": [
    {
      "folder": "INBOX",
      "uid": "12345",
      "status": "EXPORTED",            // EXPORTED | SKIPPED_ALREADY_PROCESSED | SKIPPED_NOT_FOUND | SKIPPED_NO_CONTENT | FAILED
      "errorCategory": null,           // closed set on FAILED only: { INTERNAL_ERROR }; null otherwise
      "errorMessage": null             // fixed sanitized copy + exception simple name on FAILED; null otherwise
    }
  ]
}
```

**Status / errorCategory invariants (closed sets — lock in tests):**
- `EXPORTED` → `errorCategory == null`, `errorMessage == null` (`processContent` returned `true`).
- `SKIPPED_ALREADY_PROCESSED` / `SKIPPED_NOT_FOUND` / `SKIPPED_NO_CONTENT` → `errorCategory == null`, `errorMessage == null`.
- `FAILED` → `errorCategory == INTERNAL_ERROR`, `errorMessage` = fixed copy + `ex.getClass().getSimpleName()` (NEVER raw `ex.getMessage()`; never pass raw `Throwable` to SLF4J — `feedback_sanitize_throwable_cause_for_log_emission`).
- `SKIPPED_NO_CONTENT` = `processContent` returned `false` (rule's action type produced nothing ingestable for this message), mirroring the live-run `no_content` skip (`:876-878`).

**Top-level (non-per-row) errors:**
- empty/null `selections`, null `targetFolderId`, over-cap, account/rule mismatch, account-not-found, folder-not-found → `IllegalArgumentException` → **400** (RestExceptionHandler).
- non-admin → **403**; unauthenticated → **401**.
- connection failure to the mail server (the single `connect(account)` for the whole batch fails) → top-level failure, **not** per-row. **Gate ruling:** throw a top-level `IllegalStateException` with a fixed sanitized copy and let the **existing** handler map it consistently with other mail failures — do **not** introduce a new HTTP mapping in this slice, and do **not** leak server detail.

## 6. Affected surfaces

### Backend
- `MailAutomationController.java` — new `POST /rules/{id}/preview/export` (admin) delegating to a new service method. Ships with a `*SecurityTest` calibration: 401 / 403 (USER) / 200 (admin) per the controller-security-test pattern (CLAUDE.md).
- `MailFetcherService.java` — new `exportPreviewMatches(accountId, ruleId, targetFolderId, selections)` orchestrator (no `comment` — dropped in §5). Reuses `connect`, `resolveMessageUid`, `collectAttachmentParts`, `processContent`, `buildMailProperties`, `wouldProcessContent`, and the `existsByAccountIdAndFolderAndUid` guard. Per-row try/catch → tagged outcome; sanitized `INTERNAL_ERROR`. Opens the folder `READ_ONLY` (D1). New public records `MailRulePreviewExportRequest`, `MailRulePreviewExportResult`, `MailRulePreviewExportRow`, and a status enum — co-located with the existing preview records.
- **Transaction boundary (OOS #3 guardrail):** if per-row failure isolation across document writes is needed, wrap **`processContent(...)` only** in one outer per-row `TransactionTemplate` (REQUIRES_NEW) — exactly as bulk-declare wrapped its row call. **Do NOT change the ingestion helper/service signatures or internals** (`processContent` / `ingestEmailMessage` / `ingestAttachments` / `emailIngestionService` / `uploadService` stay as-is). The boundary lives in the new orchestrator, never inside the reused ingestion code. `previewRule` itself runs without an outer DB transaction, so confirm during implementation whether the wrap is actually needed rather than adding it reflexively.

### Frontend
- `MailAutomationPage.tsx` — in the existing preview dialog (`previewDialogOpen`, result in `previewResult.matches`), add: per-row checkboxes, an **"Export selected to folder"** action, and a per-row outcome panel after export (grouped by status, mirroring the bulk-declare failed/skip alerts). Selection sends `[{folder, uid}]`.
  - **Target folder — reuse the page's existing idiom, do NOT build a folder-tree picker.** The rule form already takes a folder via a **folder-path text field + folder-id-override text field** resolved through the page's `resolveFolderId()` helper (`MailAutomationPage.tsx:2047-2060`, `:5145-5146`). The export dialog reuses that same path/UUID-resolution pattern for `targetFolderId`. (A `FolderTree` component exists at `components/browser/FolderTree.tsx`, but adopting it here would be scope creep against the in-page convention — flag if the gate prefers it.)
  - **Unprocessable rows are selectable** (D2): a `processable: false` row may be exported; it simply returns `SKIPPED_NO_CONTENT`. Do **not** disable those checkboxes — the contract handles them explicitly, so disabling would make the rule implicit and confuse the operator.
  - **Re-export drain (mirror bulk-declare):** on partial outcome, uncheck the successfully-handled selections (`EXPORTED` + the `SKIPPED_*` rows) from the checkbox state so a retry re-submits only the `FAILED` rows — same UX contract as the bulk-declare textarea drain (`4bc9856`). Do not silently leave them checked.
- `mailAutomationService.ts` — new `exportPreviewMatches(ruleId, request)` with a response-shape predicate guard (tolerate null vs omitted per `feedback_guard_predicates_real_backend_shape_drift`), a Phase-5 HTML-fallback sentinel (`feedback_phase5_mocked_html_fallback`), and **inspect the per-row `status`/counts, not just HTTP 200** (`feedback_http_success_is_not_semantic_success`).
- `types`/interfaces for the new request/response in the mail service module.

### Audit
- Export reuses the existing ingestion path; whatever per-document activity/audit `emailIngestionService.ingestEmail` / `uploadService.uploadDocument` emit on a normal rule run is emitted identically here. **No new audit channel for the export action itself.** (A grep of `EmailIngestionService` showed no direct audit emission; implementation should confirm the ingestion services' existing behavior rather than add a channel — inoculate against scope creep here.)

### Schema
- **None.** No new table, no migration. Documents land via the existing ingestion path; no export-job persistence (scheduling is OOS).

## 7. Reused patterns / sizing

- **Per-row partial success** — orchestration mirrors bulk-declare (`RecordsManagementService.declareRecordsBulk`) and legal-hold; tagged per-row outcome, sanitized `INTERNAL_ERROR`, status-keyed invariants.
- **Estimated size:** Medium (1-2 pd). Backend orchestrator + records + security test (~1 pd); frontend selection UI + folder picker + outcome rendering + service guard/tests (~1 pd). No schema work.
- **Risk:** Medium. Re-fetch-by-uid against a live IMAP store is the main unknown (uid stability, folder reopen). Non-destructive default (D1) keeps blast radius low — worst case is duplicate staged documents, never mailbox or source loss. Quota: ingestion consumes tenant quota via the existing dual-layer enforcement (CLAUDE.md) — no new quota logic, but a large export can hit the quota ceiling; surface quota-exceeded as a per-row `FAILED`/`INTERNAL_ERROR` or a top-level 4xx (decide with the existing ContentService behavior).

## 8. Memory checklist applied to this brief

- `feedback_sanitize_throwable_cause_for_log_emission` — §5 `FAILED` rule + no raw throwable to SLF4J.
- `feedback_phase5_mocked_html_fallback` — dedicated sentinel for the new route in the service test.
- `feedback_contract_nullability_must_be_explicit` — all per-row optional fields (`errorCategory`, `errorMessage`) marked nullable in §5.
- `feedback_http_success_is_not_semantic_success` — frontend inspects per-row `status`/counts.
- `feedback_guard_predicates_real_backend_shape_drift` — predicate guards tolerate null vs omitted; calibrate strictness against the E2E gate, not unit mocks.
- `feedback_brief_paths_must_be_grep_verified` / `feedback_cross_package_brief_preflight` — every path/endpoint above is grep-verified against current code (preview endpoint `:893`, ingestion core `:2148-2225`, preview records `:1159-1188`).
- `feedback_per_slice_fix_commit_stages_code_and_test` — at implementation time, stage co-located new `.test.ts(x)` with the code; re-check `git status` has zero in-scope `??`.

## 9. Gate rulings (adjudicated 2026-05-24)

All decision points resolved; the §5/§6 contract reflects these.

- **D1** — accepted: non-destructive export. `READ_ONLY`, no `applyMailAction`, no mark-SEEN, no `recordProcessedMail`.
- **D2** — accepted: explicit selected `[{folder, uid}]`.
- **D3** — accepted: required `targetFolderId`.
- **D4** — accepted: skip already-processed; UI must carry the caveat that **re-export itself can duplicate** (D1 records nothing).
- **D5** — accepted: `SKIPPED_NOT_FOUND`.
- **D6** — accepted: hard cap. Use the existing debug-max ceiling if stable/easy, else a fixed 200 — **lock the chosen cap in tests** either way.
- **D7** — accepted: omit `documentIds` for v1; deep-links are a fast-follow, not worth breaching the ingestion OOS.
- **Connection failure** — accepted: top-level `IllegalStateException` + fixed sanitized copy, mapped by the existing handler; no new HTTP mapping (§5).
- **Per-row transaction** — accepted wording: wrap **`processContent(...)` only** in one outer per-row `TransactionTemplate` if needed; never change ingestion helper signatures/internals (§6).

Remaining implementation-time calibration (not gate decisions): whether the per-row transaction wrap is actually required (verify against `emailIngestionService`/`uploadService`); exact cap value if the debug ceiling proves unstable.

## 10. What this brief does not commit to

- No track opened by this document; no code/test/frontend/schema/`.env` change; no commits by the brief itself.
- Implementation begins now that the gate has adjudicated §9, per the established discovery → brief → gate → implement cadence.

## 11. Verification (this brief)

```bash
git status --short                                  # M .env + this brief only
git diff --stat -- 'ecm-core/src/'                  # empty
git diff --stat -- 'ecm-frontend/'                  # empty
git diff --stat -- 'ecm-core/src/main/resources/'   # empty (no migration)
```
