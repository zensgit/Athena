# P3 PR-11 / PR-12 / PR-13 / PR-14 / PR-15 Execution Plan

## Date
- 2026-04-14

## Status
- completed

## Context
- `P0A`, `P0B`, `P1`, and the first `P2` wave are closed.
- `P3` should resume larger enterprise backlog items only where the current codebase is ready to support them.
- This backlog cannot be planned as five unrelated greenfield features. Current code inspection shows:
  - Athena already has a substantial mail-specific OAuth baseline in:
    - `ecm-core/src/main/java/com/ecm/core/integration/mail/model/MailAccount.java`
    - `ecm-core/src/main/java/com/ecm/core/integration/mail/service/MailOAuthService.java`
    - `ecm-core/src/main/java/com/ecm/core/integration/mail/controller/MailAutomationController.java`
    - `ecm-core/src/main/java/com/ecm/core/integration/mail/service/MailFetcherService.java`
  - deployment docs already support env-backed OAuth credential resolution via `oauthCredentialKey`:
    - `docs/INSTALLATION.md`
  - there is no first-class generic secret storage or property-encryption layer in `ecm-core`
  - there is no first-class LDAP/AD sync package in `ecm-core`
  - there is no first-class legal hold / disposition schedule implementation in `ecm-core`

## Recommendation
- `PR-11`: secret abstraction and property-encryption foundation
- `PR-12`: generic OAuth credential store, built by generalizing the existing mail OAuth path
- `PR-13`: LDAP / Active Directory sync
- `PR-14`: legal hold foundation
- `PR-15`: disposition schedules

## Why This Order
- `Generic OAuth Credential Store` is not a true greenfield item. Athena already has provider-aware OAuth connect, callback, reset, refresh, and env-backed credential resolution for mail automation.
- Rebuilding OAuth storage first without a shared secret abstraction would just duplicate one-off secret handling in a second subsystem.
- `LDAP / AD sync` is operationally valuable, but it is mostly orthogonal to the mail-OAuth write set and can move after secret/OAuth normalization without blocking the repository core.
- `Disposition Schedules` should not start before `Legal Holds`. Otherwise Athena would implement destruction/transfer logic without the mandatory blocking control that prevents disposal of held content.
- `Records Management` remains explicitly deferred until `Legal Holds` and `Disposition Schedules` both exist and are stable.

## Safe Parallelization Guidance
- `Claude Code CLI` can be called as a read-only sidecar for bounded audits or UI/API gap analysis.
- Do not let `Claude CLI` and the main implementation edit the same write set simultaneously.
- Safe split examples:
  - main line: `PR-11` / `PR-12` backend secret and OAuth refactor
  - sidecar: `PR-13` LDAP field and scheduler audit
  - main line: `PR-14` hold lifecycle rules
  - sidecar: `PR-15` disposition API and reporting surface audit

## PR-11: Secret Abstraction And Property Encryption Foundation

### Goal
- Stop spreading secret-bearing fields and ad hoc encryption expectations across feature code.
- Create a single backend abstraction for resolving, storing, rotating, and redacting sensitive values.

### Current State
- `MailAccount.password` is still plain persistence with a `Should be encrypted in prod` comment.
- Mail OAuth currently mixes:
  - env-backed credentials through `oauthCredentialKey`
  - DB-backed tokens on `mail_accounts`
- No reusable property-encryption layer exists for dynamic model-backed properties.

### Proposed Scope
- introduce a shared secret-resolution abstraction for:
  - env-backed secrets
  - DB-backed encrypted payloads
  - redacted API projections
- add encrypted-value persistence support with key version metadata
- define the first reusable encrypted storage path before expanding to generic OAuth
- keep search/indexing rules explicit:
  - encrypted values are not indexed
  - redacted views never leak cleartext

### Primary Write Set
- new `ecm-core/src/main/java/com/ecm/core/security/secret/` package
- new `ecm-core/src/main/java/com/ecm/core/security/crypto/` package
- `ecm-core/src/main/java/com/ecm/core/integration/mail/model/MailAccount.java`
- `ecm-core/src/main/java/com/ecm/core/integration/mail/service/MailOAuthService.java`
- `ecm-core/src/main/java/com/ecm/core/integration/mail/service/MailFetcherService.java`
- `ecm-core/src/main/resources/application.yml`
- Liquibase changeset for encrypted secret storage if DB-backed secret records are introduced

### Exit Gate
- Secret-bearing code paths resolve through one shared abstraction.
- No API response exposes raw encrypted payloads or secrets.
- Mail OAuth remains functional after the abstraction is introduced.

### Suggested Split
- `PR-11A` mail secret crypto foundation
- `PR-11B` model-backed property encryption adoption

### PR-11A Status
- completed
- delivered:
  - shared `security/secret` codec foundation
  - transparent encryption for `MailAccount.password`, `oauthClientSecret`, `oauthAccessToken`, and `oauthRefreshToken`
  - legacy plaintext compatibility without schema migration
  - targeted persistence/unit coverage and full backend regression pass
- references:
  - [P3_PR11A_SECRET_CRYPTO_FOUNDATION_DESIGN_20260414.md](/Users/chouhua/Downloads/Github/Athena/docs/P3_PR11A_SECRET_CRYPTO_FOUNDATION_DESIGN_20260414.md)
  - [P3_PR11A_SECRET_CRYPTO_FOUNDATION_VERIFICATION_20260414.md](/Users/chouhua/Downloads/Github/Athena/docs/P3_PR11A_SECRET_CRYPTO_FOUNDATION_VERIFICATION_20260414.md)
- deferred to `PR-11B`:
  - dynamic model-backed property encryption
  - broader adoption outside mail-backed secret fields

### PR-11B Status
- completed
- delivered:
  - Liquibase `079` for encrypted property definitions and node encrypted-property storage
  - dictionary-backed encrypted-property policy on `PropertyDefinition`
  - `NodePropertyEncryptionService` for write-path encryption, readable projection, and index sanitization
  - model validation for `encrypted` semantics and cross-storage property-usage detection
  - controller/DTO/search integration so encrypted properties stay readable at the API surface but stay out of Elasticsearch
  - minimal frontend authoring and visibility in `ContentModelsPage`
- references:
  - [P3_PR11B_MODEL_PROPERTY_ENCRYPTION_DESIGN_20260414.md](/Users/chouhua/Downloads/Github/Athena/docs/P3_PR11B_MODEL_PROPERTY_ENCRYPTION_DESIGN_20260414.md)
  - [P3_PR11B_MODEL_PROPERTY_ENCRYPTION_VERIFICATION_20260414.md](/Users/chouhua/Downloads/Github/Athena/docs/P3_PR11B_MODEL_PROPERTY_ENCRYPTION_VERIFICATION_20260414.md)
- deferred beyond `PR-11`:
  - backfill of legacy plaintext node properties
  - masking/redaction UX for runtime property editors
  - per-property key-management workflows

## PR-12: Generic OAuth Credential Store

### Goal
- Generalize the existing mail-specific OAuth implementation into a reusable credential-store capability that other integrations can consume.

### Current State
- Athena already supports mail OAuth authorize/callback/token refresh/reset flows.
- Existing OAuth state is tied to `MailAccount` and mail-specific provider assumptions.
- Operations already understand env-driven credential keys; this should be preserved rather than discarded.

### Proposed Scope
- introduce a provider-agnostic OAuth credential aggregate:
  - provider
  - subject / owner
  - scopes
  - access token
  - refresh token
  - expiry
  - credential-key indirection where env-backed mode is required
- refactor mail automation to consume the generic store instead of owning the full token model itself
- preserve backward compatibility for:
  - existing `mail_accounts.oauth_*` fields during migration
  - `oauthCredentialKey` env resolution
- expose minimal admin APIs for:
  - connect status
  - reset / disconnect
  - reauth-required state

### Suggested Split
- `PR-12A` generic OAuth token lifecycle service plus mail adapter/facade generalization
- `PR-12B` first-class OAuth credential aggregate, migration, and broader admin surface

### Primary Write Set
- new `ecm-core/src/main/java/com/ecm/core/integration/oauth/` package
- `ecm-core/src/main/java/com/ecm/core/integration/mail/service/MailOAuthService.java`
- `ecm-core/src/main/java/com/ecm/core/integration/mail/service/MailFetcherService.java`
- `ecm-core/src/main/java/com/ecm/core/integration/mail/controller/MailAutomationController.java`
- `ecm-core/src/main/java/com/ecm/core/integration/mail/model/MailAccount.java`
- Liquibase changeset for generic OAuth credential persistence if DB-backed storage is retained

### Exit Gate
- Mail automation uses the generic OAuth credential service instead of owning a special-case token lifecycle.
- Existing mail OAuth accounts remain operable after migration.
- Env-backed credential-key mode remains supported for operators.

### PR-12A Status
- completed
- delivered:
  - generic `integration/oauth` service boundary for authorize/callback/refresh/reset/session handling
  - `MailOAuthCredentialOwnerAdapter` bridging existing `MailAccount` persistence into the generic service
  - `MailOAuthService` reduced to a mail-facing facade
  - `MailFetcherService` and `MailAutomationController` moved onto the generic service path
  - targeted OAuth/mail regression coverage and full backend regression pass
- references:
  - [P3_PR12A_GENERIC_OAUTH_MAIL_GENERALIZATION_DESIGN_20260414.md](/Users/chouhua/Downloads/Github/Athena/docs/P3_PR12A_GENERIC_OAUTH_MAIL_GENERALIZATION_DESIGN_20260414.md)
  - [P3_PR12A_GENERIC_OAUTH_MAIL_GENERALIZATION_VERIFICATION_20260414.md](/Users/chouhua/Downloads/Github/Athena/docs/P3_PR12A_GENERIC_OAUTH_MAIL_GENERALIZATION_VERIFICATION_20260414.md)
- deferred to `PR-12B`:
  - standalone generic OAuth credential aggregate
  - Liquibase migration away from mail-owned token storage
  - broader cross-integration admin/status API

### PR-12B Status
- completed
- delivered:
  - first-class `oauth_credentials` aggregate and Liquibase backfill
  - mail adapter read preference for generic credentials with legacy mail-column mirroring
  - transactional mail controller create/update/delete synchronization
  - targeted repository/controller/adapter coverage and full backend regression pass
- references:
  - [P3_PR12B_GENERIC_OAUTH_CREDENTIAL_AGGREGATE_DESIGN_20260414.md](/Users/chouhua/Downloads/Github/Athena/docs/P3_PR12B_GENERIC_OAUTH_CREDENTIAL_AGGREGATE_DESIGN_20260414.md)
  - [P3_PR12B_GENERIC_OAUTH_CREDENTIAL_AGGREGATE_VERIFICATION_20260414.md](/Users/chouhua/Downloads/Github/Athena/docs/P3_PR12B_GENERIC_OAUTH_CREDENTIAL_AGGREGATE_VERIFICATION_20260414.md)
- deferred beyond `PR-12`:
  - dedicated cross-integration OAuth admin controller
  - removal of legacy mirrored mail token columns after more owners adopt the aggregate

## PR-13: LDAP / Active Directory Sync

### Goal
- Add first-class directory sync for users and groups without coupling it to the mail-OAuth refactor.

### Current State
- No LDAP/AD sync service/package exists in `ecm-core`.
- User/group and site-role hardening from `P1` provides a cleaner target for directory-backed membership updates.

### Proposed Scope
- add LDAP config, connection test, scheduled sync, and differential update flow
- support:
  - user create/update/disable
  - group sync
  - membership sync
  - optional nested-group handling
- keep delete semantics conservative:
  - disable or de-activate synced principals instead of hard delete

### Primary Write Set
- new `ecm-core/src/main/java/com/ecm/core/integration/ldap/` package
- `ecm-core/src/main/java/com/ecm/core/entity/User.java`
- `ecm-core/src/main/java/com/ecm/core/service/UserGroupService.java`
- `ecm-core/src/main/resources/application.yml`
- Liquibase changeset for LDAP linkage fields

### Exit Gate
- Differential sync works against a representative directory dataset.
- Disabled directory users are not left as active Athena principals.
- Group membership convergence is deterministic across repeated sync runs.

### PR-13 Status
- completed
- delivered:
  - first-class `integration/ldap` package using JNDI-based connection test and full snapshot sync
  - Liquibase `076` mirror metadata on `users` and `groups`
  - `ldap` identity-provider backend that serves reads from the local mirror and rejects direct writes
  - admin APIs for `test-connection` and `sync`
  - scheduled sync surface behind `ecm.ldap.sync.*`
  - targeted LDAP coverage and full backend regression pass
- references:
  - [P3_PR13_LDAP_SYNC_DESIGN_20260414.md](/Users/chouhua/Downloads/Github/Athena/docs/P3_PR13_LDAP_SYNC_DESIGN_20260414.md)
  - [P3_PR13_LDAP_SYNC_VERIFICATION_20260414.md](/Users/chouhua/Downloads/Github/Athena/docs/P3_PR13_LDAP_SYNC_VERIFICATION_20260414.md)
- deferred beyond `PR-13`:
  - nested-group expansion
  - delta/checkpoint sync
  - LDAP-backed authentication
  - directory-driven site membership automation

## PR-14: Legal Hold Foundation

### Goal
- Introduce the control layer required before Athena can safely automate disposition.

### Current State
- Athena does not yet have first-class hold entities, hold membership, or hold-aware delete/version pruning.
- `Disposition` was previously listed as an enterprise backlog item, but it is not safe to implement before this layer exists.

### Proposed Scope
- add hold aggregate and hold-item linkage
- block:
  - delete
  - move
  - purge
  - version prune
  - trash / permanent delete
  when an item, one of its ancestors, or one of its descendants in the target subtree is held
- add audit-visible hold create/release operations

### Primary Write Set
- new `ecm-core/src/main/java/com/ecm/core/entity/LegalHold.java`
- new `ecm-core/src/main/java/com/ecm/core/entity/LegalHoldItem.java`
- new `ecm-core/src/main/java/com/ecm/core/controller/LegalHoldController.java`
- new `ecm-core/src/main/java/com/ecm/core/service/LegalHoldService.java`
- `ecm-core/src/main/java/com/ecm/core/service/TrashService.java`
- `ecm-core/src/main/java/com/ecm/core/service/VersionService.java`
- `ecm-core/src/main/java/com/ecm/core/service/NodeService.java`
- `ecm-core/src/main/java/com/ecm/core/service/FolderService.java`
- Liquibase changeset for hold tables

### Exit Gate
- Held documents cannot be deleted or purged through any supported path.
- Held content cannot have required historical versions removed.
- Hold release re-enables normal lifecycle only after the hold is cleared.

### PR-14 Status
- completed
- delivered:
  - first-class `legal_holds` / `legal_hold_items` persistence via Liquibase `077`
  - admin-only legal hold controller and service surface
  - subtree-aware runtime blocking for:
    - node delete
    - node move
    - folder delete
    - trash move / permanent delete / empty trash / auto-purge
    - version delete
  - targeted hold coverage and full backend regression pass
- references:
  - [P3_PR14_LEGAL_HOLD_FOUNDATION_DESIGN_20260414.md](/Users/chouhua/Downloads/Github/Athena/docs/P3_PR14_LEGAL_HOLD_FOUNDATION_DESIGN_20260414.md)
  - [P3_PR14_LEGAL_HOLD_FOUNDATION_VERIFICATION_20260414.md](/Users/chouhua/Downloads/Github/Athena/docs/P3_PR14_LEGAL_HOLD_FOUNDATION_VERIFICATION_20260414.md)
- deferred beyond `PR-14`:
  - archive / transfer / bulk-import enforcement
  - frontend hold management
  - disposition integration

## PR-15: Disposition Schedules

### Goal
- Add schedule-driven cutoff, transfer, and destroy actions on top of the legal-hold control layer.

### Current State
- No first-class disposition schedule or execution history exists in `ecm-core`.
- Without `PR-14`, disposition would lack a mandatory destruction block for held content.

### Proposed Scope
- add folder-level schedules and ordered disposition actions
- add scheduler/executor with auditable runs
- block destroy actions when:
  - legal hold is active
  - prerequisite schedule state is incomplete
- first slice uses:
  - `CUTOFF`
  - `ARCHIVE`
  - `DESTROY`
- first slice integrates archive execution immediately and defers broader transfer handoff

### Primary Write Set
- new `ecm-core/src/main/java/com/ecm/core/entity/DispositionSchedule.java`
- new `ecm-core/src/main/java/com/ecm/core/entity/DispositionActionExecution.java`
- new `ecm-core/src/main/java/com/ecm/core/controller/DispositionScheduleController.java`
- new `ecm-core/src/main/java/com/ecm/core/service/DispositionScheduleService.java`
- new `ecm-core/src/main/java/com/ecm/core/service/DispositionActionExecutorService.java`
- new `ecm-core/src/main/java/com/ecm/core/service/DispositionScheduleScheduler.java`
- `ecm-core/src/main/java/com/ecm/core/service/ContentArchiveService.java`
- `ecm-core/src/main/java/com/ecm/core/service/NodeService.java`
- `ecm-core/src/main/java/com/ecm/core/service/ArchivePolicyService.java`
- Liquibase `078-create-disposition-schedules.xml`

### Exit Gate
- Scheduled cutoff/archive/destroy actions execute in order.
- Destroy is blocked while any effective legal hold exists.
- Every execution is auditable and replay-safe.

### PR-15 Status
- completed
- delivered:
  - first-class `disposition_schedules` and `disposition_action_executions` persistence via Liquibase `078`
  - admin-only CRUD, dry-run, execute-now, batch-run, and history APIs
  - replay-safe execution ledger with per-action candidate limits
  - fixed ordered backend action chain:
    - `CUTOFF`
    - `ARCHIVE`
    - `DESTROY`
  - archive execution integration through `ContentArchiveService`
  - governance-safe destroy execution through `NodeService.deleteNodeByGovernance(...)`
  - legal-hold-aware destroy blocking recorded as `BLOCKED` execution history
  - archive-policy versus disposition-schedule conflict guard on the same folder
  - targeted disposition coverage and full backend regression pass
- references:
  - [P3_PR15_DISPOSITION_SCHEDULES_DESIGN_20260414.md](/Users/chouhua/Downloads/Github/Athena/docs/P3_PR15_DISPOSITION_SCHEDULES_DESIGN_20260414.md)
  - [P3_PR15_DISPOSITION_SCHEDULES_VERIFICATION_20260414.md](/Users/chouhua/Downloads/Github/Athena/docs/P3_PR15_DISPOSITION_SCHEDULES_VERIFICATION_20260414.md)
- deferred beyond `PR-15`:
  - transfer handoff and remote-destination execution
  - frontend disposition authoring and reporting
  - richer records-management action graphs and declaration semantics

## Explicit Deferral
- `Records Management` stays deferred until after `PR-14` and `PR-15`.
- `Module/Plugin Framework`, `IMAP Server`, `FTP`, and `SMB/CIFS` remain below the current P3 priority line.

## Suggested PR Order
1. `PR-11` secret abstraction and property-encryption foundation
2. `PR-12` generic OAuth credential store
3. `PR-13` LDAP / AD sync
4. `PR-14` legal hold foundation
5. `PR-15` disposition schedules

## Conditional Swap
- If business pressure is strongly directory-driven, `PR-13` may move ahead of `PR-12`.
- Even in that case, `PR-14` must still stay ahead of `PR-15`.

## Overall Status
- `P3` planning is now re-baselined against the actual Athena codebase.
- `Generic OAuth Credential Store` is reclassified from greenfield work to generalization work.
- `Disposition Schedules` is reclassified as blocked until `Legal Holds` exists.
- `PR-11A` is complete.
- `PR-11B` is complete.
- `PR-12A` is complete.
- `PR-12B` is complete.
- `PR-13` is complete.
- `PR-14` is complete.
- `PR-15` is complete.
