# Phase 1 (P1) Implementation Notes

Date: 2026-01-30

## Scope
- Mail: rule preview endpoint + UI.
- Search: spellcheck / “Did you mean?” suggestions (ES term suggester).
- Versioning: configurable version label policy (semantic vs calendar).
- Preview: async preview queue with retry + version-created hook.
- Permissions: deny precedence + conflict resolution.
- Audit: category toggles (event family enable/disable).

## Changes by Area

### Mail Automation
- Added rule preview endpoint to dry-run a single rule match without ingesting.
- UI now exposes a “Preview” action for rules with a summary + matched-message table.

API additions:
- `POST /api/v1/integration/mail/rules/{ruleId}/preview`
  - Body: `{ "accountId": "...", "maxMessagesPerFolder": 25 }`

Key files:
- `ecm-core/src/main/java/com/ecm/core/integration/mail/service/MailFetcherService.java`
- `ecm-core/src/main/java/com/ecm/core/integration/mail/controller/MailAutomationController.java`
- `ecm-frontend/src/services/mailAutomationService.ts`
- `ecm-frontend/src/pages/MailAutomationPage.tsx`

### Search
- Added spellcheck endpoint using Elasticsearch term suggester on `content`.
- Search results UI shows “Did you mean” suggestions when no results are found.

API additions:
- `GET /api/v1/search/spellcheck?q=...&limit=5`

Key files:
- `ecm-core/src/main/java/com/ecm/core/search/FacetedSearchService.java`
- `ecm-core/src/main/java/com/ecm/core/controller/SearchController.java`
- `ecm-frontend/src/services/nodeService.ts`
- `ecm-frontend/src/pages/SearchResults.tsx`

### Version Management
- Introduced configurable label policy service.
- Version creation + initial version generation now use configured policy.

Config:
- `ecm.versioning.label-policy` (`semantic` | `calendar`)
- `ecm.versioning.calendar.format` (default `yyyy.MM.dd`)
- `ecm.versioning.calendar.include-sequence` (default `true`)
- `ecm.versioning.calendar.sequence-separator` (default `.`)

Key files:
- `ecm-core/src/main/java/com/ecm/core/service/VersionLabelService.java`
- `ecm-core/src/main/java/com/ecm/core/service/VersionService.java`
- `ecm-core/src/main/java/com/ecm/core/pipeline/processor/InitialVersionProcessor.java`
- `ecm-core/src/main/resources/application.yml`

### Preview / Rendition
- Added async preview queue with retry and system-user execution.
- Version-created events enqueue preview generation.
- New endpoint to queue preview explicitly.

API additions:
- `POST /api/v1/documents/{documentId}/preview/queue?force=false`

Config:
- `ecm.preview.queue.enabled`
- `ecm.preview.queue.poll-interval-ms`
- `ecm.preview.queue.batch-size`
- `ecm.preview.queue.max-attempts`
- `ecm.preview.queue.retry-delay-ms`
- `ecm.preview.queue.run-as-user`

Key files:
- `ecm-core/src/main/java/com/ecm/core/preview/PreviewQueueService.java`
- `ecm-core/src/main/java/com/ecm/core/event/EcmEventListener.java`
- `ecm-core/src/main/java/com/ecm/core/controller/DocumentController.java`
- `ecm-core/src/main/resources/application.yml`

### Permissions
- Deny precedence now applies across inheritance chain.
- Effective permissions calculation removes denied grants.

Key file:
- `ecm-core/src/main/java/com/ecm/core/service/SecurityService.java`

### Audit
- Added category toggles to enable/disable audit families.
- Categories are resolved by event prefix (NODE_, VERSION_, RULE_, MAIL_, etc.).

Config:
- `ecm.audit.disabled-categories` (comma/space-separated, e.g. `RULE MAIL`)

Key files:
- `ecm-core/src/main/java/com/ecm/core/service/AuditService.java`
- `ecm-core/src/main/resources/application.yml`

### Notes
- Saved searches/query templates already exist in Phase 0 and remain unchanged.
