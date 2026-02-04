# Phase 40 - MFA (Local TOTP) Development (2026-02-04)

## Scope
Implement local TOTP enrollment, verification, and disable flows alongside the existing Keycloak MFA guidance.

## Backend Changes
- Added `UserMfaSettings` entity and repository to persist per-user MFA settings.
- Added `TotpService` for secret generation, TOTP verification, and OTPAuth URI creation.
- Added `MfaService` for enrollment, verification, disable, and recovery code regeneration.
- Added `MfaController` with endpoints:
  - `GET /api/v1/mfa/status`
  - `POST /api/v1/mfa/enroll`
  - `POST /api/v1/mfa/verify`
  - `POST /api/v1/mfa/disable`
  - `POST /api/v1/mfa/recovery-codes`
- Added configuration: `ecm.mfa.issuer`.

## Database
- Liquibase change `025-add-user-mfa-settings.xml` creates `user_mfa_settings` table.

## Frontend Changes
- Settings page now loads local MFA status and provides:
  - Enrollment dialog (secret + OTPAuth URI + recovery codes)
  - Verification input
  - Disable flow
- Keycloak MFA link remains for org-level configuration.

## Files Updated
- `ecm-core/src/main/java/com/ecm/core/entity/UserMfaSettings.java`
- `ecm-core/src/main/java/com/ecm/core/repository/UserMfaSettingsRepository.java`
- `ecm-core/src/main/java/com/ecm/core/security/mfa/TotpService.java`
- `ecm-core/src/main/java/com/ecm/core/security/mfa/MfaService.java`
- `ecm-core/src/main/java/com/ecm/core/controller/MfaController.java`
- `ecm-core/src/main/resources/application.yml`
- `ecm-core/src/main/resources/db/changelog/changes/025-add-user-mfa-settings.xml`
- `ecm-frontend/src/pages/SettingsPage.tsx`
