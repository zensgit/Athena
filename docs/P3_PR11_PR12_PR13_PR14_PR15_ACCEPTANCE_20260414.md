# P3 PR-11 / PR-12 / PR-13 / PR-14 / PR-15 Acceptance Plan

## Date
- 2026-04-14

## Status
- completed

## PR-11 Secret Abstraction And Property Encryption Foundation

### Backend Acceptance
- secret-bearing values resolve through one shared abstraction instead of ad hoc per-feature logic
- encrypted persistence includes key-version metadata or an equivalent rotation marker
- secret-bearing API responses are redacted by default
- encrypted values are excluded from search indexing

### Suggested Automated Coverage
- secret resolver tests for env-backed and DB-backed paths
- encryption/decryption round-trip tests with key-version assertions
- controller/service tests confirming redacted responses
- regression coverage on existing mail OAuth flows after the abstraction lands

### Manual Smoke
1. Configure a mail account with `oauthCredentialKey`.
2. Verify the account still resolves client credentials and refresh token from env-backed configuration.
3. Verify admin/API responses do not expose cleartext secret values.

### PR-11A Acceptance Result
- partial PR acceptance met for the first foundation slice
- verified in this phase:
  - shared secret crypto abstraction exists and is reusable
  - `MailAccount` secret fields persist as encrypted values when secret protection is enabled
  - legacy plaintext values remain readable
  - existing mail controller and OAuth env flows remain green
  - targeted and full backend tests pass
- still deferred:
  - model-backed property encryption adoption outside Mail Automation

### PR-11B Acceptance Result
- acceptance met
- verified in this phase:
  - content model property definitions can carry `encrypted=true`
  - encrypted model properties are persisted into `nodes.encrypted_properties` instead of plaintext `nodes.properties`
  - encrypted model properties remain readable through API/controller projection and Alfresco compatibility reads
  - encrypted model properties are excluded from search/index property documents
  - model validation rejects invalid encrypted/indexed combinations and tracks usage across both plaintext and encrypted storage
  - frontend content-model authoring can create encrypted properties for types and aspects
  - targeted backend and frontend tests pass
- still deferred beyond `PR-11`:
  - backfill of existing plaintext node property data
  - masking/redaction behavior in runtime property editors

## PR-12 Generic OAuth Credential Store

### Backend Acceptance
- a generic OAuth credential record can represent provider, scopes, expiry, and credential-key indirection
- mail automation consumes the generic store without regressing authorize/callback/reset flows
- legacy mail OAuth data is migrated or bridged without breaking existing accounts
- reauth-required state remains visible and actionable after refactor

### Suggested Automated Coverage
- migration/bridge tests for legacy `mail_accounts.oauth_*` fields
- service tests for connect, refresh, disconnect, and reauth-required flows
- controller tests for admin reset/disconnect behavior
- regression coverage for `MailOAuthService` and `MailAutomationController`

### Manual Smoke
1. Open Mail Automation with an existing OAuth-backed account.
2. Confirm connect status, callback, and reset flows still work.
3. Confirm reconnect remains available after an injected `invalid_grant`-style failure.

### PR-12A Acceptance Result
- partial PR acceptance met for the lifecycle generalization slice
- verified in this phase:
  - generic OAuth lifecycle service exists and is reusable through owner adapters
  - Mail Automation now consumes the generic service instead of owning a special-case refresh/session path
  - existing `mail_accounts.oauth_*` persistence remains operable without a migration
  - `oauthCredentialKey` env-backed mode remains supported
  - targeted and full backend tests pass
- still deferred:
  - standalone OAuth credential aggregate
  - generic migration away from mail-owned token columns
  - broader admin/status API for non-mail integrations

### PR-12B Acceptance Result
- acceptance met
- verified in this phase:
  - first-class generic OAuth credential aggregate exists in `oauth_credentials`
  - existing mail OAuth accounts are backfilled into the new aggregate
  - Mail Automation create/update/delete/reset flows remain operational
  - encrypted token persistence is preserved in the new aggregate
  - targeted and full backend tests pass
- still deferred beyond `PR-12`:
  - dedicated cross-integration OAuth admin/status controller
  - removal of mirrored legacy mail token columns

## PR-13 LDAP / Active Directory Sync

### Backend Acceptance
- connection configuration validates against a representative directory
- sync can create or update users and groups idempotently
- disabled directory principals are deactivated in Athena rather than left active
- repeated sync runs converge without duplicate users, groups, or memberships

### Suggested Automated Coverage
- service tests for create/update/disable and nested-group handling
- scheduler tests for repeated sync and modify-timestamp checkpoints
- repository/integration tests for group membership convergence
- controller tests for connection test and sync trigger APIs

### Manual Smoke
1. Configure a test LDAP/AD source.
2. Run sync and confirm users and groups appear in Athena.
3. Disable a user in the directory and confirm Athena deactivates that principal on the next sync.

### PR-13 Acceptance Result
- acceptance met for the first LDAP/AD sync slice
- verified in this phase:
  - Athena can validate LDAP connectivity and run admin-triggered syncs
  - LDAP users, groups, and memberships converge into the local mirror without duplicate identities
  - missing mirrored principals are disabled instead of deleted
  - repeated sync runs preserve local authority keys when the directory renames usernames or group names
  - targeted and full backend tests pass
- still deferred:
  - nested-group expansion
  - checkpoint/delta sync
  - LDAP authentication
  - directory-driven site membership mapping

## PR-14 Legal Hold Foundation

### Backend Acceptance
- hold create/release is persisted and auditable
- delete, move, trash, purge, and version prune paths are blocked for held content
- inherited or ancestor-driven holds prevent descendant destruction where policy requires
- releasing the hold restores normal lifecycle behavior

### Suggested Automated Coverage
- hold service tests for create/add/remove/release
- integration tests covering:
  - held document delete
  - held document move
  - held version prune
  - held subtree destructive operation rejection
- controller security tests for admin-only hold authoring

### Manual Smoke
1. Place a document under legal hold.
2. Attempt move, delete, purge, and version removal paths.
3. Confirm each destructive path is blocked until the hold is released.

### PR-14 Acceptance Result
- acceptance met for the backend legal-hold foundation slice
- verified in this phase:
  - legal holds and held-node membership persist in first-class tables
  - admin-only hold APIs can create, inspect, and release holds
  - subtree-aware runtime blocking prevents held content from being moved, deleted, trashed, purged, or version-pruned through the guarded repository paths
  - `emptyTrash()` aborts before partial deletion when a held root item exists
  - `purgeOldTrashItems()` skips held items instead of deleting them
  - targeted and full backend tests pass
- still deferred:
  - archive / transfer / bulk-import overwrite enforcement
  - frontend hold management
  - disposition integration in `PR-15`

## PR-15 Disposition Schedules

### Backend Acceptance
- folder-level schedules execute ordered actions deterministically
- cutoff, archive, and destroy actions persist execution history
- destroy is blocked while a legal hold is active
- repeated scheduler runs are replay-safe and do not duplicate completed actions

### Suggested Automated Coverage
- schedule authoring and validation tests
- scheduler tests for ordered action advancement
- hold-aware destroy-block tests
- archive integration tests where disposition hands off content
- archive-policy conflict tests on the same folder

### Manual Smoke
1. Attach a disposition schedule to a records folder.
2. Advance time or trigger execution to reach cutoff and archive steps.
3. Confirm destroy does not run while a hold exists.
4. Release the hold and confirm destroy can proceed only when the schedule is otherwise eligible.

### PR-15 Acceptance Result
- acceptance met for the backend-first disposition slice
- verified in this phase:
  - first-class folder-level disposition schedules and execution history exist
  - admin-only APIs can author, dry-run, execute, batch-run, and inspect schedule history
  - ordered `CUTOFF -> ARCHIVE -> DESTROY` progression is deterministic and replay-safe
  - destroy execution reuses governance-safe delete semantics and is blocked by active legal holds
  - enabled archive policies cannot coexist with enabled disposition schedules on the same folder
  - targeted and full backend tests pass
- still deferred:
  - transfer execution and remote-destination handoff
  - frontend disposition authoring/reporting
  - fuller records-management constructs beyond this backend slice

## Command Baseline

```bash
cd ecm-core
./mvnw -B -Dstyle.color=never test
```

## Merge Criteria
- no `P3` work may regress `P0A`, `P0B`, `P1`, or `P2` correctness guarantees
- each PR adds targeted automated coverage for the feature-specific paths it touches
- full backend regression remains green after every merge
- destructive governance features are not merged ahead of their control dependencies

## Final Planning Decision
- `PR-11` and `PR-12` are the preferred opening pair
- `PR-13` may move earlier only for business-priority reasons
- `PR-14` must precede `PR-15`
- `Records Management` remains deferred until `PR-14` and `PR-15` are complete
- current delivery state:
  - `PR-11A approve`
  - `PR-11B approve`
  - `PR-11 approve`
  - `PR-12A approve`
  - `PR-12B approve`
  - `PR-12 approve`
  - `PR-13 approve`
  - `PR-14 approve`
  - `PR-15 approve`
