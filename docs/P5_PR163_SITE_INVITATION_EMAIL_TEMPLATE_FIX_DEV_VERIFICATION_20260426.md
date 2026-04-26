# P5 PR-163 Site Invitation Email Template Fix - Development & Verification

Date: 2026-04-26

## Context

The Gap #7 site invitation backend added `SiteInvitationService.sendInvitationEmail(...)`
with template key `site.invitation`, but the email-template seed migrations only
created the RM report preset delivery templates:

- `rm.report_preset.delivery.succeeded`
- `rm.report_preset.delivery.failed`

With `ecm.email.enabled=true`, site invitation creation would persist the
invitation row, then `EmailNotificationService` would skip delivery because no
`site.invitation` template existed.

## Design

This fix keeps the existing invitation API and table shape intact and adds the
missing email delivery contract.

Changes:

- Added Liquibase change `087-seed-site-invitation-email-template.xml`.
- Included `087` from `db.changelog-master.xml` after `086-create-site-invitations.xml`.
- Seeded default plain-text template key `site.invitation`.
- Added a `MARK_RAN` precondition so environments that already created a custom
  `site.invitation/default` template are not broken by the migration.
- Extended `SiteInvitationService` variables to include every placeholder used
  by the seeded template:
  - `siteTitle`
  - `siteId`
  - `invitedBy`
  - `token`
  - `role`
  - `message`
  - `expiresAt`
- Added `SiteInvitationServiceTest` to lock the service-template contract.
- Added `LiquibaseChangelogParseTest` so Backend Verify catches included
  changelog parse errors before Phase C has to start a full Docker stack.

## Files Changed

- `ecm-core/src/main/java/com/ecm/core/service/SiteInvitationService.java`
- `ecm-core/src/main/resources/db/changelog/db.changelog-master.xml`
- `ecm-core/src/main/resources/db/changelog/changes/087-seed-site-invitation-email-template.xml`
- `ecm-core/src/test/java/com/ecm/core/config/LiquibaseChangelogParseTest.java`
- `ecm-core/src/test/java/com/ecm/core/service/SiteInvitationServiceTest.java`

## Verification

Local checks:

```text
git diff --check
PASS

xmllint --noout \
  ecm-core/src/main/resources/db/changelog/changes/087-seed-site-invitation-email-template.xml \
  ecm-core/src/main/resources/db/changelog/db.changelog-master.xml
PASS

javac -cp "$(find ecm-core/.m2-cache/repository -name '*.jar' | tr '\n' ':')" \
  -d /tmp/athena-liquibase-test-classes \
  ecm-core/src/test/java/com/ecm/core/config/LiquibaseChangelogParseTest.java
PASS
```

Follow-up validation note:

- CI run `24955914630` passed Backend Verify and Frontend Build & Test, but
  Phase C startup exposed a Liquibase XML schema-order issue in `087`.
- Root cause: `<preConditions>` was placed after `<comment>` inside the
  `changeSet`; the repository's Liquibase XSD expects preconditions before
  comments/changes.
- Fix: move `<preConditions>` before `<comment>` in `087`.
- Guardrail: `LiquibaseChangelogParseTest` now parses
  `db/changelog/db.changelog-master.xml` through Liquibase during Backend Verify.

Target backend test attempted:

```text
cd ecm-core
./mvnw -Dtest=SiteInvitationServiceTest test
```

Result:

```text
Cannot connect to the Docker daemon at unix:///Users/chouhua/.docker/run/docker.sock. Is the docker daemon running?
```

The local Maven wrapper is Docker-backed in this checkout, and the local Docker
daemon is not available. Backend compile/test validation therefore must be
confirmed by GitHub Actions `Backend Verify` after push.

## Expected CI Signal

The remote CI run should validate:

- Backend compile, including the new service test.
- Liquibase changelog loading through `db.changelog-master.xml`.
- Existing frontend build/test and E2E gates remain unaffected because this is a
  backend email-template contract fix.

## Risk

Low. The migration is additive and protected by a precondition. The service
change only replaces an inline `Map.of(...)` with a mutable map carrying the
same existing variables plus `message` and `expiresAt`.
