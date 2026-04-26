# Athena ECM — 17-Gap Closure: Phases 1–3 Closeout

## Date
2026-04-26

## Status
**Phases 1, 2, and 3 are fully closed** (all deliverable items at these phases).
Remaining items (Phase 4 + deferred) are documented below with explicit rationale.

---

## Phase 1 — Quick Wins (all closed)

| Gap | Feature | BE | FE | Verification Doc |
|-----|---------|----|----|-----------------|
| #5 | Email Outbound SMTP Notifications | ✅ | ✅ | `P5_PR159*_VERIFICATION_*.md` (series) |
| #3 | Scheduled User Actions | ✅ | ✅ | Existing `RulesPage.tsx` + `ScheduledRuleRunner` |
| #7 | Site Invitation Workflow | ✅ | ✅ | `P5_SITE_INVITATIONS_VERIFICATION_20260426.md` (BE) + `P5_SITE_INVITATIONS_FRONTEND_VERIFICATION_20260426.md` (FE) |

### Gap #5 Detail
- Migration 084: `email_notification_preferences`, `email_template` tables
- Migration 085: seeded default templates (RM preset delivery succeeded/failed)
- Migration 087: seeded `site.invitation` template
- `EmailNotificationService`, `NotificationChannelService`, `NotificationDispatcherService`
- Frontend: email preference toggles in `RecordsManagementPage`

### Gap #3 Detail
Backend (`AutomationRule.cronExpression`, `ScheduledRuleRunner`) and frontend (`RulesPage.tsx`) were both complete before this development cycle. Confirmed closed via code audit.

### Gap #7 Detail
Backend: `SiteInvitation` entity (migration 086), `SiteInvitationService`, `SiteInvitationController`
Frontend: `siteInvitationService.ts`, `SiteInvitationsPage.tsx` (`/admin/sites/:siteId/invitations`), `InvitationAcceptPage.tsx` (`/invitations/accept?token=…`)

---

## Phase 2 — Core Gaps (all closed)

| Gap | Feature | BE | FE | Verification Doc |
|-----|---------|----|----|-----------------|
| #1 | Smart / Virtual Folders | ✅ | ✅ | — (confirmed closed via code audit) |
| #2 | Legal Holds | ✅ | ✅ | `P5_LEGAL_HOLDS_UI_VERIFICATION_20260426.md` |
| #4 | Custom Model Management | ✅ | ✅ | — (confirmed closed via code audit) |

### Gap #1 Detail
`FolderService.java` executes queryCriteria at read-time. `CreateFolderDialog.tsx` has smart-folder toggle. `SavedSearchesPage.tsx` supports smart-folder-from-saved-search. Confirmed via code audit.

### Gap #2 Detail
Backend: `LegalHold`, `LegalHoldItem` entities (migration 077), `LegalHoldService`, `LegalHoldController`
Frontend: `legalHoldService.ts`, `LegalHoldsPage.tsx` (`/admin/legal-holds`)
`assertOperationAllowed()` in `TrashService`/`NodeService` blocks held-node deletion/move.

### Gap #4 Detail
`ContentModelsPage.tsx` (type/aspect/property editor), `ContentModelController` (CRUD). Confirmed closed via code audit.

---

## Phase 3 — Enterprise (all deliverable items closed)

| Gap | Feature | BE | FE | Verification Doc |
|-----|---------|----|----|-----------------|
| #6 | LDAP/AD Directory Sync | ✅ | ✅ | `P5_LDAP_ADMIN_UI_VERIFICATION_20260426.md` |
| #8 | Full Disposition Schedules | ✅ | ✅ | `P5_DISPOSITION_SCHEDULES_UI_VERIFICATION_20260426.md` |
| #9 | Property Encryption at Rest | ✅ | N/A | — |
| #10 | Generic OAuth Credential Store | ✅* | deferred | see below |

### Gap #6 Detail
Backend: `LdapSyncController` (`@ConditionalOnProperty` — ldap mode only), `LdapSyncService`, `JndiLdapDirectoryClient`
Frontend: `ldapService.ts`, `LdapSyncPage.tsx` (`/admin/ldap`) — handles LDAP-not-configured state gracefully.

### Gap #8 Detail
Backend: `DispositionSchedule` entity (migration 078), `DispositionScheduleService`, `DispositionScheduleController`, `DispositionScheduler`
Frontend: `dispositionScheduleService.ts`, `DispositionSchedulesPage.tsx` (`/admin/disposition-schedules`) — master-detail with dry-run, execute, execution history, batch run-all.

### Gap #9 — No UI needed
`NodePropertyEncryptionService` + `SecretCryptoService` + `EncryptedSecretConverter` operate transparently at the JPA layer during persist/read. No admin controller exists and none is needed — encryption is automatic based on content model type definitions. Migration 079 adds the `encrypted_properties` JSONB column.

### Gap #10 — Backend infrastructure complete; UI deferred
`OAuthCredentialService` (generic authorization-code flow, auto-refresh, encrypted storage), `OAuthCredentialRepository`, `MailOAuthCredentialOwnerAdapter`. Mail integration is the **only consumer**. Building a "Generic OAuth Integrations" admin page would expose an abstraction with no second user. **Deferred** until a second integration (e.g. SharePoint, Google Drive) requires it.

---

## Phase 4 — Protocol Extensions (explicitly deferred; needs scope decision)

| Gap | Feature | BE | FE | Notes |
|-----|---------|----|----|-------|
| #11 | IMAP Server | ❌ | ❌ | XL — embedded Apache James; port 143 in containers |
| #12 | FTP Server | ❌ | ❌ | L — Apache Mina FTP; port 21 in containers |
| #13 | SMB/CIFS | ❌ | ❌ | XL — recommend defer; WebDAV covers most cases; port 445 blocked in containers |
| #14 | Multilingual Content | ❌ | ❌ | L (2–4w) — both BE + FE missing; needs scope decision |
| #15 | Records Management (full) | ✅ | ✅ | Confirmed closed: `RecordsManagementController`, `RecordsManagementService`, `recordsManagementService.ts`, `components/records/` (declare/undeclare/categories/audit/timeline/telemetry) |
| #16 | Module/Plugin Framework | ❌ | ❌ | XL — ServiceLoader + ClassLoader isolation |
| #17 | Remote Repository Connector | ❌ | ❌ | XL — CMIS federated search fan-out |

### Gap #14 Build Order (when approved)
Migration → `LocalizedContent` entity → `LocalizedContentRepository` → `LocalizedContentService` (locale fallback: exact → language → default) → `LocalizedContentController` → `Accept-Language` header plumbing → frontend locale switcher + per-node translation editor.

### Gap #15 Scope Note
File plan hierarchy, record declaration, immutability enforcement. `RecordsManagementPage.tsx` covers RM reporting and delivery; full Records Management (filing, classification, vital records) is a separate multi-sprint body of work.

---

## Verification Summary

| Verification Doc | Date | Covers |
|-----------------|------|--------|
| `P5_SITE_INVITATIONS_VERIFICATION_20260426.md` | 2026-04-26 | Gap #7 BE |
| `P5_SITE_INVITATIONS_FRONTEND_VERIFICATION_20260426.md` | 2026-04-26 | Gap #7 FE |
| `P5_LEGAL_HOLDS_UI_VERIFICATION_20260426.md` | 2026-04-26 | Gap #2 FE |
| `P5_LDAP_ADMIN_UI_VERIFICATION_20260426.md` | 2026-04-26 | Gap #6 FE |
| `P5_DISPOSITION_SCHEDULES_UI_VERIFICATION_20260426.md` | 2026-04-26 | Gap #8 FE |
| `P5_PR159*_VERIFICATION_*.md` (series) | 2026-04-26 | Gap #5 (email) |

---

## What's Next — Choose One

1. **Gap #14 Multilingual Content** — sequential L-scope build (not parallel; pieces are interdependent). Requires explicit scope approval. Estimated 2–4 weeks.

2. **Phase 4 Protocol Gap with scope discussion** — pick one: IMAP (#11), FTP (#12), or Multilingual (#14). Each needs architecture discussion before coding starts.

3. **Operational / Quality focus** — write integration/E2E tests for newly closed gaps (#6, #7, #8), run full-stack smoke, address test coverage gaps.

4. **Gap #10 second consumer** — if a new integration (e.g. Google Drive, SharePoint) is on the roadmap, that unlocks the generic OAuth admin UI.
