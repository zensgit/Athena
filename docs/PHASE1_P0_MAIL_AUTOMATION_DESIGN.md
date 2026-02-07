# Mail Automation P0 — Credential Masking + OAuth Token Preservation

Date: 2026-02-06

## Context
We compared Athena's mail automation UX against reference implementations in:
- `reference-projects/paperless-ngx/src/paperless_mail/serialisers.py` (Obfuscated password field)
- `reference-projects/paperless-ngx/src/paperless_mail/views.py` (preserve password/refresh token when masked)

In Athena today, editing a mail account does not indicate whether a password is configured and can inadvertently overwrite it. Also, OAuth refresh tokens are cleared whenever account settings are updated, which breaks an already-connected OAuth account.

## Goals
1. Preserve existing mail passwords when users edit an account without changing credentials.
2. Preserve OAuth refresh tokens for connected accounts unless the account switches away from OAuth or switches to env-based credentials.

## Non-Goals
- Redesign the mail automation UI.
- Add new mail rule actions or ingestion modes.

## Design
### 1) Masked password UX + safe updates
- Add a `passwordConfigured` flag to mail account responses so the UI can render a masked placeholder without exposing secrets.
- In the edit dialog, prefill password with a mask (e.g., `********`) only when a password exists.
- On save, ignore masked/blank password values to avoid overwriting stored credentials.

**Implementation**
- Backend:
  - `MailAutomationController.MailAccountResponse` adds `passwordConfigured` derived from stored password.
  - `updateAccount` ignores password updates when value is all `*` (masked).
- Frontend:
  - Use a `PASSWORD_MASK` constant for edit form display.
  - When editing, populate the password field with the mask if configured.
  - When saving a non-OAuth account, strip blank/masked passwords from the update payload.

### 2) Preserve OAuth refresh tokens on account edits
- Only clear stored OAuth tokens if the account:
  - Switches away from OAuth, or
  - Switches to env-based OAuth credentials (`oauthCredentialKey` set).
- Preserve stored tokens for OAuth accounts that rely on DB-stored refresh tokens.

**Implementation**
- `MailAutomationController.applyOAuthSettings` now clears OAuth secrets only when needed. It no longer wipes tokens for OAuth accounts without a credential key.

## Files Changed
- `ecm-core/src/main/java/com/ecm/core/integration/mail/controller/MailAutomationController.java`
- `ecm-frontend/src/pages/MailAutomationPage.tsx`
- `ecm-frontend/src/services/mailAutomationService.ts`

## Follow-up Ideas (from reference comparison)
- Mail rule ingestion options from paperless-ngx (full .eml + attachments vs attachments-only).
- Mail preview / diagnostics skip-reason parity with paperless-ngx tests.
- Audit trail / retention and permission inheritance patterns inspired by alfresco-community-repo.

