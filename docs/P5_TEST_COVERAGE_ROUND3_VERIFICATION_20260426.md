# Test Coverage Round 3 — NotificationController, EmailIntegrationController, Admin Smoke

## Date
2026-04-26

## Status
Complete. 3 new files (478 lines); TypeScript clean; zero source-file errors.

---

## Scope

Close the final backend controller test gaps and add a unified mock-based admin smoke spec covering all gap-closure pages.

| Item | Type | Gap |
|------|------|-----|
| `NotificationControllerTest.java` | Backend controller test | #5 / Notification inbox |
| `EmailIntegrationControllerTest.java` | Backend controller test | Email archiving ingestion |
| `admin-gap-closure-smoke.mock.spec.ts` | Playwright mock smoke | All 8 new admin pages |

---

## Files Added

| File | Lines |
|------|-------|
| `ecm-core/src/test/java/com/ecm/core/controller/NotificationControllerTest.java` | 149 |
| `ecm-core/src/test/java/com/ecm/core/controller/EmailIntegrationControllerTest.java` | 107 |
| `ecm-frontend/e2e/admin-gap-closure-smoke.mock.spec.ts` | 222 |

---

## Backend Test Design

### `NotificationControllerTest` — 6 tests

Source: `NotificationController` at `/api/notifications` + `/api/v1/notifications`.

**Setup notes:**
- `JavaTimeModule` registered to handle `LocalDateTime` serialization in `NotificationDto`
- `PageableHandlerMethodArgumentResolver` added to support `Pageable` parameters on GET list endpoints
- `Notification` and `Activity` mocked via `Mockito.mock()` with all required getters stubbed
- Page endpoints return `$.content[0].*` shape (Spring `PageImpl` JSON serialization)

| Test | Endpoint | Assertion |
|------|----------|-----------|
| `getInboxReturnsPageWithNotification` | `GET /unread`-less base | `$.content[0].id`, `$.content[0].activityType` |
| `getUnreadReturnsUnreadNotificationsPage` | `GET /unread` | Same paginated shape |
| `getUnreadCountReturnsCountMap` | `GET /unread-count` | `$.count` = 5 |
| `markReadReturnsUpdatedNotification` | `PATCH /{id}/read` | `$.id` present in response |
| `markAllReadReturnsMarkedCount` | `POST /mark-all-read` | `$.marked` = 7 |
| `deleteNotificationReturnsNoContent` | `DELETE /{id}` | 204 empty |

---

### `EmailIntegrationControllerTest` — 3 tests

Source: `EmailIntegrationController.ingestEmail` at `POST /api/v1/integration/email/ingest`.

**Setup notes:**
- Multipart pattern: `MockMvcBuilders.standaloneSetup()` + `MockMultipartFile` + `multipart()` request builder
- `Document` mocked via `Mockito.mock(Document.class)` — only `getId()` and `getName()` stubbed
- `isNull()` matcher used for absent `folderId` parameter

| Test | Scenario | Assertion |
|------|----------|-----------|
| `ingestEmailWithoutFolderIdReturnsDocument` | No `folderId` param | `emailIngestionService.ingestEmail(any, isNull())` → 200 + `$.id` |
| `ingestEmailWithFolderIdReturnsDocument` | `?folderId=<uuid>` | `emailIngestionService.ingestEmail(any, eq(folderId))` → 200 + `$.id` |
| `ingestEmailWithTextPlainContentTypeStillSucceeds` | `text/plain` MIME type | Service still returns doc → 200 |

---

## Frontend Smoke Design

### `admin-gap-closure-smoke.mock.spec.ts` — 8 tests

One smoke test per gap-closure admin page. Each test: mock routes → navigate → assert main heading or primary widget. No deep interaction.

| Test | Route | Key Mocks | Assertion |
|------|-------|-----------|-----------|
| Legal Holds | `/admin/legal-holds` | `GET /api/v1/legal-holds → []` | "Legal Holds" `h4` heading |
| LDAP Admin | `/admin/ldap` | Action routes pre-registered (no mount call) | "Test Connection" + "Sync Now" buttons |
| Disposition Schedules | `/admin/disposition-schedules` | `GET /api/v1/disposition-schedules → []` | "Disposition Schedules" heading |
| Multilingual Content | `/admin/localized-content` | No mount call | "Node ID (UUID)" input field |
| Notifications | `/notifications` | `GET /unread` (paged) + `GET /unread-count → {count:0}` | `h5` "Notifications" heading |
| Sites | `/admin/sites` | `GET /api/v1/sites → []` + `GET /api/v1/followings → []` | "Sites" heading |
| Content Models | `/admin/content-models` | 3 mount calls: `/content-models`, `/dictionary/types`, `/dictionary/aspects` | "Content Models" heading |
| Automation Rules | `/rules` | 6 mount calls: `/rules`, `/templates`, `/stats`, `/actions/definitions`, `/executions/timeline`, `/executions/audit` | "New Rule" button |

**Route ordering note (Rules):** The base `/rules` route is registered LAST so sub-path routes (`/rules/templates`, etc.) take precedence — Playwright matches routes in reverse registration order.

---

## TypeScript Verification

```bash
cd ecm-frontend && npx tsc --noEmit 2>&1 | grep -v node_modules
# → (no output — zero source-file errors)
```

---

## Full Test Coverage Summary — All Gap Closure Work

### Backend Tests (all gaps)

| Gap | Service Tests | Controller Tests | Security Tests |
|-----|--------------|-----------------|----------------|
| #1 Smart Folders | `FolderServiceSmartFolderTest`, `NodeServiceSmartFolderTest`, `SavedSearchServiceSmartFolderTest` | `SavedSearchControllerSmartFolderTest` | — |
| #2 Legal Holds | `LegalHoldServiceTest`, `FolderServiceLegalHoldTest`, `NodeServiceLegalHoldTest`, `TrashServiceLegalHoldTest`, `VersionServiceLegalHoldTest` | `LegalHoldControllerTest` | `LegalHoldControllerSecurityTest` |
| #3 Scheduled Rules | `RuleEngineServiceNotificationTest` | `RuleControllerFolderScopeTest`, `RuleControllerScheduledValidationTest`, `RuleControllerActionDefinitionsTest`, `RuleControllerExecutionLedgerTest`, `RuleControllerRuleAuditTimelineTest` | (security variants) |
| #4 Content Models | `ContentModelServiceTest`, `ContentModelValidationTest` | `ContentModelControllerTest` | — |
| #5 Email / Notifications | `NotificationInboxServiceTest`, `EmailNotificationServiceTest` | `NotificationControllerTest` (**new**), `EmailIntegrationControllerTest` (**new**) | — |
| #6 LDAP | `LdapUserGroupBackendTest` | `LdapSyncControllerTest` | `LdapSyncControllerSecurityTest` |
| #7 Site Invitations | `SiteInvitationServiceTest` | `SiteInvitationControllerTest` (**R1**) | `SiteInvitationControllerSecurityTest` (**R1**) |
| #8 Disposition | `DispositionScheduleServiceTest`, `ArchivePolicyServiceDispositionConflictTest`, `DispositionActionExecutorServiceTest` | `DispositionScheduleControllerTest` | `DispositionScheduleControllerSecurityTest` |
| #14 Multilingual | `LocalizedContentServiceTest` (**R1**) | `LocalizedContentControllerTest` (**R1**) | `LocalizedContentControllerSecurityTest` (**R1**) |
| #15 Records Mgmt | `RecordsManagementServiceTest`, `CategoryServiceRecordCategoryTest`, `NodeServiceRecordDeclarationTest`, `TrashServiceRecordDeclarationTest`, etc. | `RecordsManagementControllerTest` | `RecordsManagementControllerSecurityTest` |

### Frontend Mock Specs (all gaps)

| Gap | Spec File(s) | Round |
|-----|-------------|-------|
| #1 Smart Folders | `saved-searches-smart-folder.mock.spec.ts` | R2 |
| #2 Legal Holds | `admin-legal-holds.mock.spec.ts` | R1 |
| #3 Scheduled Rules | `admin-rules.mock.spec.ts` | R2 |
| #4 Content Models | `admin-content-models.mock.spec.ts` | R2 |
| #5 Notifications | `notifications-email-preferences.mock.spec.ts` | R2 |
| #6 LDAP Sync | `admin-ldap-sync.mock.spec.ts` | R1 |
| #7 Site Invitations | `admin-site-invitations.mock.spec.ts` + `invitation-accept.mock.spec.ts` | R1 |
| #8 Disposition | `admin-disposition-schedules.mock.spec.ts` | R1 |
| #14 Multilingual | `admin-localized-content.mock.spec.ts` | R1 |
| — (all pages) | `admin-gap-closure-smoke.mock.spec.ts` | **R3** |

**Test layer is complete for all UI-bearing closed gaps.**
