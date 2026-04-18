# P3 PR-12B Generic OAuth Credential Aggregate Design

## Date
- 2026-04-14

## Status
- implemented

## Goal
- Promote OAuth credentials from a mail-owned implementation detail to a first-class backend aggregate.
- Keep Mail Automation operational during the migration by mirroring state between the new aggregate and existing `mail_accounts.oauth_*` columns.

## Scope
- Added first-class persistence:
  - `ecm-core/src/main/java/com/ecm/core/integration/oauth/model/OAuthCredential.java`
  - `ecm-core/src/main/java/com/ecm/core/integration/oauth/repository/OAuthCredentialRepository.java`
  - `ecm-core/src/main/resources/db/changelog/changes/075-create-oauth-credentials.xml`
- Updated mail bridge:
  - `ecm-core/src/main/java/com/ecm/core/integration/mail/service/MailOAuthCredentialOwnerAdapter.java`
- Updated admin entry points to keep the aggregate in sync:
  - `ecm-core/src/main/java/com/ecm/core/integration/mail/controller/MailAutomationController.java`
- Added repository, adapter, and controller coverage.

## Why This Completes PR-12
- `PR-12A` already generalized the OAuth lifecycle service.
- The remaining missing piece was durable, provider-agnostic credential ownership outside `mail_accounts`.
- `PR-12B` adds that aggregate and backfills existing mail OAuth state into it.
- Existing mail admin endpoints remain the operator-facing surface, but they now work through:
  - a generic lifecycle service
  - a first-class OAuth credential table

## Design

### Aggregate Shape
- New table: `oauth_credentials`
- Key fields:
  - `owner_type`
  - `owner_id`
  - `provider`
  - `token_endpoint`
  - `tenant_id`
  - `scope`
  - `credential_key`
  - `access_token`
  - `refresh_token`
  - `token_expires_at`
- Ownership is unique on:
  - `(owner_type, owner_id)`

### Encryption Model
- `access_token` and `refresh_token` use the existing `EncryptedSecretConverter`.
- This reuses the `PR-11A` secret foundation instead of inventing a second storage path.

### Migration Strategy
- Liquibase `075` creates `oauth_credentials`.
- The same file backfills rows from `mail_accounts` for existing OAuth-enabled mail accounts.
- Backfill copies raw stored values as-is, which is safe because:
  - legacy plaintext remains readable
  - `enc:<version>:...` values also remain readable through the converter

### Compatibility Strategy
- `mail_accounts.oauth_*` columns are retained for now.
- `MailOAuthCredentialOwnerAdapter` now:
  - prefers the generic aggregate on read
  - mirrors refreshed/cleared token state back to `MailAccount`
  - can upsert/delete the generic aggregate when mail account settings change
- This preserves:
  - current controller response shapes
  - current mail fetch skip/connected heuristics
  - rollback safety during the transition period

### Controller Write Paths
- `MailAutomationController.createAccount(...)`
  - persists the `MailAccount`
  - synchronizes the generic OAuth aggregate in the same transaction
- `MailAutomationController.updateAccount(...)`
  - persists the `MailAccount`
  - synchronizes or deletes the aggregate depending on `security`
- `MailAutomationController.deleteAccount(...)`
  - deletes the aggregate
  - deletes the mail account
- These endpoints are now `@Transactional` to avoid partial drift between the two stores.

### Adapter Read/Write Semantics
- `loadOwner(...)`
  - reads the mail account for owner identity and fallback values
  - reads `oauth_credentials` as the preferred OAuth source
- `saveTokens(...)`
  - updates the generic aggregate
  - mirrors current token state to `MailAccount`
- `clearTokens(...)`
  - clears both stores
- `syncAccount(...)`
  - creates/updates the aggregate for OAUTH2 accounts
  - removes the aggregate for non-OAuth accounts

## Explicit Non-Goals
- No generic cross-integration OAuth admin controller yet
- No removal of legacy `mail_accounts.oauth_*` columns yet
- No non-mail adapter yet

## Risks And Mitigations
- Risk: partial write drift between `mail_accounts` and `oauth_credentials`
  - Mitigation: controller create/update/delete paths are now transactional
- Risk: provider does not return a new refresh token on refresh
  - Mitigation: adapter preserves the existing refresh token and writes that effective value into the aggregate
- Risk: schema/entity mismatch
  - Mitigation: JPA token-endpoint column length was aligned to Liquibase (`512`)

## Follow-On
- `PR-13`: LDAP / Active Directory sync
- Future cleanup after more integrations adopt the generic store:
  - stop mirroring legacy mail token columns
  - drop obsolete `mail_accounts.oauth_*` token fields in a later migration
