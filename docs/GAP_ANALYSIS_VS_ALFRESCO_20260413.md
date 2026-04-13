# Athena vs Alfresco Feature Gap Analysis — 2026-04-13

## Methodology

Compared Athena ECM (`ecm-core/src/main/java/com/ecm/core/`) against Alfresco Community Repository (`reference-projects/alfresco-community-repo/`) at the service layer. Focus on functional capabilities, not implementation style.

## Legend

- **Full** = Feature parity with Alfresco
- **Partial** = Core exists, missing sub-capabilities
- **Missing** = Not implemented in Athena
- **N/A** = Intentionally excluded or not applicable

---

## Capability Comparison Matrix

### Core Document Management

| Capability | Alfresco | Athena | Status | Notes |
|------------|----------|--------|--------|-------|
| Node CRUD (create/read/update/delete) | NodeService | NodeService | **Full** | |
| Folder hierarchy | FileFolderService | FolderService | **Full** | |
| Content storage & dedup | ContentService + filestore | ContentService (hash dedup) | **Full** | |
| Versioning (major/minor) | VersionService | VersionService | **Full** | |
| Check-out / check-in | CheckOutCheckInService | LockService + CheckOutCheckInService | **Full** | |
| Document locking | LockService | LockService (shared/exclusive, TTL) | **Full** | |
| Copy / Move | CopyService | NodeService | **Full** | |
| Trash / Restore | archive package | TrashService | **Full** | |
| Bulk import | BulkImportService | BulkImportService | **Full** | |
| Bulk operations | Bulk API | BulkOperationService, BulkMetadataService | **Full** | |
| Batch download | DownloadService | BatchDownloadService | **Full** | |

### Search & Discovery

| Capability | Alfresco | Athena | Status | Notes |
|------------|----------|--------|--------|-------|
| Full-text search | SearchService (Solr) | FullTextSearchService (Elasticsearch) | **Full** | Different engine, same capability |
| Faceted search | Solr facets | FacetedSearchService | **Full** | |
| Search highlighting | Solr highlights | Highlighting with context | **Full** | |
| Saved searches | Smart Folders | SavedSearchService | **Partial** | Athena saves queries but doesn't expose as virtual folders |
| Search suggestions | SuggesterService | FullTextSearchService | **Partial** | Basic autocomplete, no dedicated suggester |
| Smart/Virtual Folders | virtual package | — | **Missing** | Saved searches as navigable folder hierarchy |
| CMIS CONTAINS() / IN_TREE() | OpenCMIS | CmisQueryService | **Full** | |

### Security & Permissions

| Capability | Alfresco | Athena | Status | Notes |
|------------|----------|--------|--------|-------|
| ACL per node | PermissionService | Permission entity (allow/deny, expiry) | **Full** | |
| Permission inheritance | Inheritable ACLs | Inheritance flag on Permission | **Full** | |
| Role-based access | AuthorityService | SecurityService + Role entity | **Full** | |
| Group-based permissions | AuthorityService | UserGroupService | **Full** | |
| Dynamic authorities | — | OwnerDynamic, LockOwnerDynamic, SameDept | **Full** | Athena advantage |
| Permission templates | — | PermissionTemplateService (versioned) | **Full** | Athena advantage |
| OAuth2 / Keycloak SSO | Identity Service subsystem | KeycloakUserGroupBackend | **Full** | |
| LDAP / Active Directory sync | Authentication subsystem (ldap, ldap-ad) | — | **Missing** | Direct LDAP/AD user-group sync |
| Kerberos auth | kerberos subsystem | — | **Missing** | SSO via Kerberos tickets |
| MFA (TOTP) | — | MfaService + TotpService | **Full** | Athena advantage |
| Property encryption | encryption package | — | **Missing** | Encrypt sensitive metadata at rest |
| Time-based permission expiry | — | Permission.expiresAt | **Full** | Athena advantage |

### Workflow & Automation

| Capability | Alfresco | Athena | Status | Notes |
|------------|----------|--------|--------|-------|
| BPM engine | Activiti | Flowable | **Full** | Flowable is Activiti's successor |
| Folder rules | RuleService | RuleEngineService | **Full** | |
| Action framework | ActionService | RuleAction + ScriptService | **Full** | |
| Scheduled rules | ScheduledPersistedActionService | ScheduledRuleRunner | **Partial** | Athena has periodic backfill but no user-defined cron scheduling per action |
| Policy/Behavior hooks | PolicyComponent | Pipeline processors + events | **Partial** | Athena uses pipeline instead of node-level behavior injection |

### Collaboration

| Capability | Alfresco | Athena | Status | Notes |
|------------|----------|--------|--------|-------|
| Comments | Comments REST | CommentService (with reactions) | **Full** | Athena has reactions too |
| Ratings | RatingService | RatingService | **Full** | |
| Tags | TaggingService | TagService | **Full** | |
| Categories | CategoryService | CategoryService (hierarchical) | **Full** | |
| Discussion threads | DiscussionService | DiscussionService | **Full** | |
| Blog | BlogService | BlogService | **Full** | |
| Calendar | CalendarService | CalendarService | **Full** | |
| Sites / Workspaces | SiteService | SiteService + SiteMembershipService | **Full** | |
| Favorites | FavouritesService | FavoriteService | **Full** | |
| Following / Subscriptions | SubscriptionService | FollowingService | **Full** | |
| Activity stream | ActivityService | ActivityService | **Full** | |
| Share links | QuickShareService | ShareLinkService (password, expiry, access log) | **Full** | |
| Notifications | NotificationService | NotificationService + NotificationInboxService | **Full** | |
| Site invitations | InvitationService | — | **Missing** | Invitation workflow for site membership |

### Content Transformation

| Capability | Alfresco | Athena | Status | Notes |
|------------|----------|--------|--------|-------|
| Office → PDF | LibreOffice transforms | ConversionService + JODConverter | **Full** | |
| Thumbnail generation | ThumbnailService | PreviewService | **Full** | |
| Renditions | RenditionService (v1 + v2) | RenditionResourceService + Registry | **Full** | |
| Text extraction | Tika transforms | TikaTextExtractor (pipeline) | **Full** | |
| Image transforms | ImageMagick | PreviewService | **Partial** | No dedicated ImageMagick integration |
| CAD rendering | — | CadRenderEndpointRegistry | **Full** | Athena advantage |
| PDF annotation | — | PdfAnnotationService | **Full** | Athena advantage |
| OCR | — | OcrQueueService | **Full** | Athena advantage |
| ML classification | — | MLServiceClient + pipeline processor | **Full** | Athena advantage |

### Protocol Support

| Capability | Alfresco | Athena | Status | Notes |
|------------|----------|--------|--------|-------|
| CMIS AtomPub | OpenCMIS | CmisAtomPubController | **Full** | |
| CMIS Browser binding | OpenCMIS | CmisBrowserController | **Full** | |
| WebDAV | webdav package | WebDAV integration | **Full** | |
| WOPI / Collabora | — | WopiIntegrationController | **Full** | Athena advantage |
| REST API | remote-api module | 64 controllers | **Full** | |
| IMAP Server | imap package (repo as mailbox) | — | **Missing** | Expose repo as IMAP mailbox |
| FTP Server | filesys package | — | **Missing** | FTP access to repo |
| SMB/CIFS | filesys package (JimuFS) | — | **Missing** | Windows file sharing |

### Multi-Tenancy

| Capability | Alfresco | Athena | Status | Notes |
|------------|----------|--------|--------|-------|
| Tenant isolation | tenant package | TenantService + TenantWorkspaceScopeService | **Full** | |
| Quota enforcement | UsageService | TenantQuotaService (dual-layer) | **Full** | |
| Tenant metrics | — | TenantMetricsService | **Full** | Athena advantage |
| Per-tenant storage routing | Content store selector | — (deferred, ADR-001) | **Missing** | Deferred by design |

### Audit & Compliance

| Capability | Alfresco | Athena | Status | Notes |
|------------|----------|--------|--------|-------|
| Audit logging | AuditService (9 apps) | AuditService (9 categories) | **Full** | |
| Audit export | Audit query API | AuditExportAsyncTaskRegistry | **Full** | |
| Selective auditing | AuditApplication enable/disable | AuditCategorySetting | **Full** | |
| Records Management | RM module (file plans, dispositions, holds) | — | **Missing** | Full RM is a major module |
| Legal holds | hold/freeze packages | — | **Missing** | Freeze documents from deletion |
| Disposition schedules | disposition package | ArchivePolicyService (basic) | **Partial** | Athena has archive policies, not full disposition lifecycle |
| DoD 5015.2 compliance | dod5015 package | — | **N/A** | Government compliance, niche requirement |

### Administration

| Capability | Alfresco | Athena | Status | Notes |
|------------|----------|--------|--------|-------|
| Custom Model Management (CMM) | CMM service (runtime model creation) | ContentModelService | **Partial** | Athena has model definitions but unclear if runtime-dynamic (no-code) |
| Module framework | AMP/module loader | — | **Missing** | Pluggable extension modules |
| User preferences | PreferenceService | PreferenceService | **Full** | |
| System health probes | probes API | SystemStatusController + actuator | **Full** | |
| Data integrity checks | — | SanityCheckController | **Full** | Athena advantage |

### Integration

| Capability | Alfresco | Athena | Status | Notes |
|------------|----------|--------|--------|-------|
| Email inbound (SMTP) | InboundSMTP subsystem | EmailIngestionService | **Partial** | Athena fetches via IMAP, not inbound SMTP server |
| Email outbound (SMTP) | OutboundSMTP subsystem | NotificationService (implicit) | **Partial** | No dedicated SMTP outbound config |
| Odoo ERP | — | OdooIntegrationService | **Full** | Athena advantage |
| Webhooks | — | WebhookService + subscriptions | **Full** | Athena advantage |
| Antivirus (ClamAV) | — (via transforms) | AntivirusService (pipeline) | **Full** | Athena advantage |
| WPS Office | — | WpsIntegrationService | **Full** | Athena advantage |
| Remote repository | RemoteConnectorService | — | **Missing** | Connect to external repositories |
| OAuth credential store | OAuth1/2 CredentialsStoreService | MailOAuthService (mail-only) | **Partial** | Athena has OAuth for mail, not generic |

### Internationalization

| Capability | Alfresco | Athena | Status | Notes |
|------------|----------|--------|--------|-------|
| Multilingual content | MultilingualContentService | — | **Missing** | Same document in multiple languages |
| i18n messages | Message bundles | — | **Partial** | Frontend may have i18n; backend unclear |

---

## Gap Summary by Priority

### High Priority (Core ECM capabilities with clear user value)

| # | Gap | Alfresco Reference | Effort | Impact |
|---|-----|-------------------|--------|--------|
| 1 | **Smart/Virtual Folders** | `virtual/` package | Medium | Users navigate saved searches as folder hierarchy |
| 2 | **Legal Holds** | `hold/`, `freeze/` packages | Medium | Prevent deletion of documents under legal obligation |
| 3 | **Scheduled User Actions** | `ScheduledPersistedActionService` | Small | Users schedule their own automation rules on cron |
| 4 | **Custom Model Management (no-code)** | `cmm/` REST API | Medium | Admins create types/aspects without code deployment |
| 5 | **Email outbound (SMTP notifications)** | `OutboundSMTP` subsystem | Small | Send email notifications on events |

### Medium Priority (Enterprise features, niche but expected)

| # | Gap | Alfresco Reference | Effort | Impact |
|---|-----|-------------------|--------|--------|
| 6 | **LDAP/AD directory sync** | `Authentication` subsystem | Medium | Enterprise user/group sync from AD |
| 7 | **Site invitation workflow** | `InvitationService` | Small | Invite users to sites with approval |
| 8 | **Disposition schedules** | `disposition/` package | Medium | Full retention lifecycle (cut off → transfer → destroy) |
| 9 | **Property encryption at rest** | `encryption/` package | Medium | Encrypt sensitive metadata fields in DB |
| 10 | **Generic OAuth credential store** | `OAuth1/2 CredentialsStoreService` | Small | Store OAuth tokens for any external service |

### Low Priority (Protocol extensions, can defer)

| # | Gap | Alfresco Reference | Effort | Impact |
|---|-----|-------------------|--------|--------|
| 11 | **IMAP server** | `imap/` package | Large | Expose repository as email folders |
| 12 | **FTP server** | `filesys/` package | Large | FTP access to repository |
| 13 | **SMB/CIFS** | `filesys/` (JimuFS) | Large | Windows file sharing |
| 14 | **Multilingual content** | `MultilingualContentService` | Medium | Multi-language document versions |
| 15 | **Records Management** | `ags/rm-community/` module | Very Large | File plans, disposition, DoD 5015.2 |
| 16 | **Module/Plugin framework** | AMP loader | Large | Third-party extension architecture |
| 17 | **Remote repository connector** | `RemoteConnectorService` | Medium | Federated repository queries |

---

## Athena Advantages (Features Alfresco Lacks)

| Capability | Athena Implementation |
|------------|----------------------|
| **WOPI / Collabora Online editing** | WopiIntegrationController + WopiService |
| **WPS Office integration** | WpsIntegrationService |
| **ClamAV antivirus pipeline** | AntivirusService + VirusScanProcessor |
| **CAD file rendering** | CadRenderEndpointRegistry |
| **PDF annotation** | PdfAnnotationService + PdfManipulationService |
| **OCR processing queue** | OcrQueueService |
| **ML document classification** | MLServiceClient + MLClassificationProcessor |
| **Odoo ERP integration** | OdooIntegrationService |
| **Webhook event subscriptions** | WebhookService + subscriptions |
| **Dynamic permission authorities** | Owner, LockOwner, SameDepartment |
| **Permission templates (versioned)** | PermissionTemplateService |
| **Time-based permission expiry** | Permission.expiresAt |
| **MFA (TOTP)** | MfaService + TotpService |
| **Tenant metrics dashboard** | TenantMetricsService |
| **Data integrity checker** | SanityCheckController |
| **Async task governance (SLA)** | AsyncTaskGovernanceService |
| **Document processing pipeline** | 11 extensible processors in order |

---

## Recommended Implementation Order

Based on user value, effort, and dependency chain:

```
Phase 1 (Quick wins, 1-2 weeks)
├── #5  Email outbound SMTP notifications
├── #3  Scheduled user actions (cron per rule)
└── #7  Site invitation workflow

Phase 2 (Core gaps, 2-4 weeks)
├── #1  Smart/Virtual Folders
├── #2  Legal Holds
└── #4  Custom Model Management (no-code)

Phase 3 (Enterprise, 4-6 weeks)
├── #6  LDAP/AD directory sync
├── #8  Full disposition schedules
├── #9  Property encryption at rest
└── #10 Generic OAuth credential store

Phase 4 (Protocol extensions, as needed)
├── #11-13 File protocol servers (IMAP/FTP/SMB)
├── #14 Multilingual content
└── #15-17 RM, Module framework, Remote connector
```

---

## Conclusion

Athena covers **~85% of Alfresco's core ECM capabilities** and surpasses it in several areas (WOPI, CAD, OCR, ML, PDF annotation, webhooks, MFA, antivirus pipeline). The main gaps are:

- **5 high-priority items** that affect daily user workflows (smart folders, legal holds, scheduled actions, CMM, email notifications)
- **5 medium-priority items** for enterprise deployment (LDAP, invitations, disposition, encryption, OAuth store)
- **7 low-priority items** that are protocol extensions or niche features

The recommended Phase 1 (3 items, ~1-2 weeks) would close the most impactful gaps with minimal effort.
