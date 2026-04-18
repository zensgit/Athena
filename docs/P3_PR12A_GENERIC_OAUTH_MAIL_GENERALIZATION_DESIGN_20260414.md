# P3 PR-12A Generic OAuth Mail Generalization Design

## Date
- 2026-04-14

## Status
- implemented

## Goal
- Generalize Athena's existing mail-specific OAuth token lifecycle into a reusable backend service without introducing a second risky schema migration immediately after `PR-11A`.
- Move Mail Automation off its private authorize/refresh/reset implementation and onto a provider-agnostic OAuth service boundary.

## Why This Slice First
- Athena already had a working OAuth surface in Mail Automation:
  - authorize URL generation
  - callback exchange
  - refresh-token lifecycle
  - reauth-required handling
  - operator-facing reset flow
- The smallest safe generalization was not a brand-new OAuth aggregate plus migration.
- The safer first slice was:
  - one generic token lifecycle service
  - one owner-adapter contract
  - one production adapter using existing `mail_accounts.oauth_*` storage
- This preserves compatibility while proving the generic service shape before `PR-12B` expands persistence and admin API surface.

## Scope
- Added generic OAuth backend package:
  - `ecm-core/src/main/java/com/ecm/core/integration/oauth/OAuthProviderType.java`
  - `ecm-core/src/main/java/com/ecm/core/integration/oauth/OAuthCredentialOwner.java`
  - `ecm-core/src/main/java/com/ecm/core/integration/oauth/OAuthEnvironmentStatus.java`
  - `ecm-core/src/main/java/com/ecm/core/integration/oauth/OAuthCredentialOwnerAdapter.java`
  - `ecm-core/src/main/java/com/ecm/core/integration/oauth/OAuthReauthRequiredException.java`
  - `ecm-core/src/main/java/com/ecm/core/integration/oauth/OAuthTokenErrorParser.java`
  - `ecm-core/src/main/java/com/ecm/core/integration/oauth/OAuthCredentialService.java`
- Added mail adapter:
  - `ecm-core/src/main/java/com/ecm/core/integration/mail/service/MailOAuthCredentialOwnerAdapter.java`
- Refactored mail OAuth facade and consumers:
  - `ecm-core/src/main/java/com/ecm/core/integration/mail/service/MailOAuthService.java`
  - `ecm-core/src/main/java/com/ecm/core/integration/mail/service/MailFetcherService.java`
  - `ecm-core/src/main/java/com/ecm/core/integration/mail/controller/MailAutomationController.java`
  - `ecm-core/src/main/java/com/ecm/core/integration/mail/service/MailOAuthTokenErrorParser.java`
- Added service and regression coverage for the generalized path.

## Explicit Non-Goals
- No first-class generic OAuth credential entity yet
- No Liquibase migration for a standalone OAuth credential table yet
- No generic cross-integration admin controller yet
- No non-mail OAuth adapter yet

## Design

### Generic Service Boundary
- `OAuthCredentialService` now owns:
  - authorize URL generation
  - OAuth `state` storage and expiry
  - callback exchange
  - session cache
  - refresh-token flow
  - invalid-grant to reauth-required conversion
  - environment readiness checks
  - token clearing and session eviction
- It is keyed by:
  - `ownerType`
  - `ownerId`

### Adapter Model
- `OAuthCredentialOwnerAdapter` isolates owner-specific persistence and env-key conventions.
- The first production adapter is `MailOAuthCredentialOwnerAdapter`.
- The adapter is responsible for:
  - loading the owner
  - persisting refreshed tokens
  - clearing tokens
  - mapping owner-specific provider fields
  - constructing env-key names for:
    - provider-backed mode
    - `oauthCredentialKey` indirection mode

### Mail Compatibility Strategy
- `MailOAuthService` remains the controller-facing facade.
- Existing controller flows and response shapes stay mail-specific.
- Internally, `MailOAuthService` now delegates to `OAuthCredentialService` and only converts:
  - mail owner identity
  - env-check result shape
  - reauth exception type
- Existing `mail_accounts.oauth_*` columns remain the source of truth in this slice.

### Refresh And Reauth Semantics
- Refresh requests are now centralized in `OAuthCredentialService`.
- On token-endpoint `invalid_grant`:
  - stored tokens are cleared through the adapter
  - in-memory session cache is evicted
  - a generic `OAuthReauthRequiredException` is raised
- `MailOAuthService` converts that into the existing `MailOAuthReauthRequiredException` so downstream behavior remains stable.

### Environment Resolution
- Provider-backed mode still resolves shared env keys like:
  - `ECM_MAIL_OAUTH_GOOGLE_CLIENT_ID`
  - `ECM_MAIL_OAUTH_GOOGLE_CLIENT_SECRET`
- `oauthCredentialKey` mode still resolves owner-scoped keys like:
  - `ECM_MAIL_OAUTH_<KEY>_CLIENT_ID`
  - `ECM_MAIL_OAUTH_<KEY>_REFRESH_TOKEN`
- `PR-12A` keeps operator behavior intact rather than introducing a new config surface.

### Reset Flow
- `MailAutomationController.resetOAuth(...)` no longer manually nulls token fields.
- It delegates token clearing to the generic service path, then reloads the saved account and returns the usual response.
- This removes one more controller-specific secret mutation path.

## Risks And Mitigations
- Risk: over-claiming `PR-12` completion
  - Mitigation: document `PR-12A` as generic lifecycle generalization only; standalone OAuth aggregate remains deferred to `PR-12B`
- Risk: breaking existing mail OAuth accounts
  - Mitigation: keep `mail_accounts.oauth_*` as the persisted backing store in this slice
- Risk: inconsistent env-key behavior during refactor
  - Mitigation: env key construction moved into the adapter and covered with targeted tests

## Follow-On
- `PR-12B`: introduce a first-class generic OAuth credential aggregate, migration path, and broader admin/status surface
- `PR-13`: LDAP / Active Directory sync remains independent and can continue as a read-only audited parallel stream
