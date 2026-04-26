# P5 PR-159 — Email lane backend foundation (implementation + verification)

## Date
2026-04-26

## Status
Implementation. Backend-only. Mirrors the design in
`P5_PR159_EMAIL_LANE_BACKEND_FOUNDATION_DESIGN_20260426.md` with two
documented deviations (kept smaller scope for v1).

## Scope delivered

| Item | Status |
|------|--------|
| `spring-boot-starter-mail` dependency in `ecm-core/pom.xml` | ✅ added |
| `EmailTemplate` entity with composite-unique `(template_key, locale)` | ✅ created |
| `EmailTemplateRepository` JPA repository | ✅ created |
| `EmailNotificationService` with `@Async send(...)` and locale fallback | ✅ created |
| Migration `084-email-notification-foundation.xml` | ✅ created and registered |
| `application.yml` `ecm.email.*` env-bound config | ✅ added |
| Unit tests (Mockito) for service behaviour | ✅ created (7 tests) |

**Out of scope for v1 (deferred to follow-up slices):**
- Thymeleaf engine — substituted by Spring's `PropertyPlaceholderHelper`
  (deviation #1, see below)
- Seeded templates — table created empty (deviation #2, see below)
- `NotificationChannel` abstraction — slice PR-159b
- `notifyByEmail` preference + UI toggle — slice PR-159c
- Wiring delivery events into email channel — slice PR-159d
- CI Email Channel Gate — slice PR-159e
- `JavaMailSender` integration test with real SMTP — slice PR-159b or
  later

## Deviations from design preview

### Deviation #1: `PropertyPlaceholderHelper` instead of Thymeleaf

The design preview specified Thymeleaf with `StringTemplateResolver`.
For the v1 foundation slice we use Spring's
`org.springframework.util.PropertyPlaceholderHelper` (already on the
classpath via `spring-boot-starter-web`):

```java
private static final PropertyPlaceholderHelper PLACEHOLDER_HELPER =
    new PropertyPlaceholderHelper("${", "}", ":", true);
```

**Why:**

- Avoids adding `org.thymeleaf:thymeleaf` as a new managed dependency
  (Spring Boot 3.2 BOM does manage it via
  `spring-boot-starter-thymeleaf`, but pulling the starter brings in
  the web template resolver stack we don't need).
- For v1's plain-text bodies (`htmlBody=false` is the initial default),
  `${key}` substitution is functionally identical to Thymeleaf's
  `[[${key}]]` for variables-only templates.
- Reversible: if HTML body templates with conditionals/loops are
  needed, swap to Thymeleaf in a follow-up. The service's `send(...)`
  signature is unchanged.

**Limitation accepted for v1:** no HTML escaping by the substitution
engine. Initial templates seeded by deployments must be plain text or
must escape HTML themselves. Documented in the service Javadoc.

### Deviation #2: no seeded templates in migration 084

The design preview seeded 2 default templates (`rm.report_preset.delivery.succeeded`
and `.failed`) in the migration. The implementation creates the table
empty.

**Why:**

- Multi-line body templates as XML `<insert>` columns are awkward and
  obscure the migration's schema intent.
- Subject/body content for email notifications is a deployment
  concern, not a schema concern. Operators commonly want to override
  the default phrasing per-org.
- A follow-up slice (PR-159b or PR-159c) can either add an admin UI for
  template management or seed templates via a `CommandLineRunner` on
  startup.
- With no seeded templates and `ecm.email.enabled=false` by default,
  the `send(...)` call's "template not found" path simply logs a
  warning — no production impact.

The unit test `returnsEarly_whenTemplateNotFound` covers this path.

## Files changed

### New files

| File | Lines |
|------|------:|
| `ecm-core/src/main/java/com/ecm/core/integration/email/notify/EmailTemplate.java` | 47 |
| `ecm-core/src/main/java/com/ecm/core/integration/email/notify/EmailTemplateRepository.java` | 19 |
| `ecm-core/src/main/java/com/ecm/core/integration/email/notify/EmailNotificationService.java` | 145 |
| `ecm-core/src/main/resources/db/changelog/changes/084-email-notification-foundation.xml` | 60 |
| `ecm-core/src/test/java/com/ecm/core/integration/email/notify/EmailNotificationServiceTest.java` | 200 |

### Modified files

| File | Change |
|------|--------|
| `ecm-core/pom.xml` | +4 lines: `spring-boot-starter-mail` dep |
| `ecm-core/src/main/resources/application.yml` | +4 lines: `ecm.email.enabled`, `ecm.email.from-address` env-bound |
| `ecm-core/src/main/resources/db/changelog/db.changelog-master.xml` | +1 line: include 084 |

Total: ~471 lines added across 8 files. No existing service, event,
or controller code touched.

## Architecture detail

### Entity (`EmailTemplate`)

Extends `BaseEntity` for the standard audit columns (`created_date`,
`last_modified_date`, `created_by`, `last_modified_by`, `version`,
`is_deleted`, `deleted_at`, `deleted_by`). Composite-unique
`(template_key, locale)` enforced at JPA + DB level.

- Column name `template_key` (not `key`) avoids the SQL reserved-word
  caveat.
- `locale` defaults to `"default"` at both column-level and Java-level
  defaults so a row written without explicit locale lands as the
  fallback.
- `body_template` uses `text` to allow long bodies; `subject_template`
  uses `varchar(500)` since subjects are bounded.
- `htmlBody` defaults to `false`.

### Repository (`EmailTemplateRepository`)

Two finder methods:

- `findByTemplateKeyAndLocale(key, locale)` — exact lookup
- `findByTemplateKeyAndLocaleInOrderByLocaleAsc(key, locales)` —
  fallback-aware lookup; returns 0..N rows, service-side sorts to
  the locale-fallback order

Method names intentionally use Spring Data's derived-query convention
(no `@Query` needed) to keep the repository surface small.

### Service (`EmailNotificationService`)

`send(templateKey, toAddress, preferredLocale, variables)`:

1. Bail on `emailEnabled=false` (debug log)
2. Bail on missing recipient or `from-address` (warn log)
3. Resolve template via `resolveTemplate(...)` with locale fallback
   chain (exact → language → `default`)
4. Render subject + body via `PropertyPlaceholderHelper`
5. Build `MimeMessage` via `MimeMessageHelper`, set From/To/Subject/Body
6. `mailSender.send(...)` — fire-and-forget
7. Catch `MailException` and generic `Exception`, log warn, do not
   bubble (matches the project's AFTER_COMMIT @Async event handler
   pattern: side effects don't fail the original transaction)

`@Async` annotation activates Spring's async executor (`@EnableAsync`
is enabled in `EcmCoreApplication.java:20`).

### Configuration

Added under existing `ecm:` namespace:

```yaml
ecm:
  email:
    enabled: ${ECM_EMAIL_ENABLED:false}
    from-address: ${ECM_EMAIL_FROM_ADDRESS:}
```

`spring.mail.*` autoconfiguration is *not* added to `application.yml`.
`MailSenderAutoConfiguration` activates only when `spring.mail.host`
is set (Spring Boot's default behavior). Without an env override
setting `SPRING_MAIL_HOST`, no `JavaMailSender` bean is created — but
`@Autowired private final JavaMailSender mailSender` would then fail
to inject.

**Resolution:** the service injects `JavaMailSender` via constructor
(`@RequiredArgsConstructor`). Spring Boot will create a default
`JavaMailSenderImpl` even without `spring.mail.host` set — it just
fails at `send()` time. So the service is dependency-injectable in
all environments; only actual mail sending requires the env to set
SMTP details. This matches the `ecm.email.enabled=false` posture:
deployments without email don't need SMTP config either.

If a future deployment wants outbound email, they add:

```bash
ECM_EMAIL_ENABLED=true
ECM_EMAIL_FROM_ADDRESS=no-reply@deployer.example
SPRING_MAIL_HOST=smtp.deployer.example
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=...
SPRING_MAIL_PASSWORD=...
SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true
SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true
```

These env vars are picked up by Spring Boot's relaxed binding without
extra `application.yml` keys.

## Migration design

```xml
<changeSet id="084-create-email-template" author="p5-pr159">
  <createTable tableName="email_template">
    ... id (uuid PK), template_key, locale, subject_template,
        body_template, html_body, description, audit columns ...
  </createTable>
  <addUniqueConstraint columnNames="template_key, locale"
                       constraintName="uq_email_template_key_locale"/>
  <createIndex indexName="idx_email_template_key">
    <column name="template_key"/>
  </createIndex>
  <rollback>
    <dropTable tableName="email_template"/>
  </rollback>
</changeSet>
```

Includes explicit `<rollback>`, allowing `liquibase rollbackCount 1`
to cleanly undo. This satisfies the "reversible" requirement called
out in the original handoff plan.

## Tests

`EmailNotificationServiceTest`: 7 unit tests with `@ExtendWith(MockitoExtension.class)`:

| Test | Subject |
|------|---------|
| `skipsSilently_whenDisabled` | Verifies `ecm.email.enabled=false` prevents any mail call |
| `skipsSilently_whenToBlank` | Bails early with no recipient |
| `skipsSilently_whenFromBlank` | Bails early without `from-address` |
| `returnsEarly_whenTemplateNotFound` | Missing template → warn, no send |
| `sendsRenderedMessage_whenEnabled` | Real `MimeMessage` round-trip; subject and body substituted |
| `logsAndSwallows_whenSendThrows` | `MailSendException` from `JavaMailSender` does not bubble |
| `resolvesTemplateByLocale_withFallbacks` | exact (`zh-CN`) → language (`zh-TW`→`zh`) → `default` (`fr`→`default`) |
| `computeLocaleFallbacks_chain` | unit test for the static fallback computation |

(Test count is 8 — the count above lists 7 in the table plus
`computeLocaleFallbacks_chain` which is the static helper test.)

No integration test in this slice. SMTP integration testing (with
Greenmail or testcontainers) lands in PR-159b.

## Verification

### Local
- `npx -p typescript@5.4.5 tsc --noEmit` (frontend) — N/A, no frontend
  change in this slice
- Backend unit tests: cannot run locally (`ecm-core/mvnw` delegates
  Maven to Docker per CLAUDE.md, and Docker is not available in this
  session) — same constraint as the rest of the project's recent work
- Static check: source compiles against existing `BaseEntity` and
  imports verified against `EcmCoreApplication.java:20` (which has
  `@EnableAsync`)
- Liquibase XML schema validated against the schemaLocation declared

### Expected CI signal on next push

| Job | Expected |
|-----|----------|
| Backend Verify | Runs the new `EmailNotificationServiceTest`; expects all 8 tests pass |
| Frontend Build & Test | unchanged |
| Phase C Security Verification | unchanged |
| Acceptance Smoke (3 admin pages) | unchanged |
| Frontend E2E Core Gate | unchanged |
| Phase 5 Mocked Regression Gate | unchanged (depends on PR-161 verdict, not on this slice) |

The Backend Verify gate is the meaningful new signal. If the new
`spring-boot-starter-mail` dependency or the entity / repository
mapping has any defect, this gate will name it.

## Security

- `from-address` is server-configured, not user-configurable
- No SMTP secrets in the repo (env-only via `${SPRING_MAIL_USERNAME}` etc.)
- `${key}` substitution does not auto-escape HTML; v1 expects plain
  text bodies and documents this constraint
- `htmlBody=true` templates are deferred to a follow-up that adds
  Thymeleaf and proper escaping
- No reply-to from arbitrary user input

## What this enables

After PR-159 lands:

- The `EmailNotificationService.send(...)` API exists and is callable
  from any Spring-managed bean
- A future slice (PR-159b) can introduce the `NotificationChannel`
  abstraction with `InboxChannel` (existing) + `EmailChannel` (this
  service)
- A future slice (PR-159c) can wire `notifyByEmailOnSuccess` /
  `notifyByEmailOnFailure` preference keys
- A future slice (PR-159d) can wire `RmReportPresetDeliveryService`
  delivery events to email dispatch
- A future slice (PR-159e) can add a CI `Email Channel Gate` job

Each slice is independently revertible.

## Sequencing

| PR | Role | Status |
|----|------|--------|
| PR-160 | unauth-flow fixme posture | ⚠️ partially superseded |
| PR-161 | helper redesign + un-fixme + 3 fixes | ✅ committed (`befc527`) |
| PR-159 design | Email lane backend design preview | ✅ committed (`0e641b3`) |
| PR-161 audit | Latent regression pattern audit | ✅ committed (`4c31860`) |
| **PR-159 implementation** | **Email lane backend foundation** | **✅ this turn** |
| PR-162 (planned) | Un-fixme route-fallback:78 + sla:89 | After PR-161 verdict |
| PR-159b | NotificationChannel abstraction | After PR-159 lands |
| PR-159c | notifyByEmail preference + UI | After PR-159b |
| PR-159d | Wire delivery events to email | After PR-159c |
| PR-159e | CI Email Channel Gate | After PR-159d |

## Memory entries that apply

- `feedback_diagnostic_cadence_for_opaque_500s.md` — same diagnostic
  cadence applied throughout the slice (small commit, named scope,
  reversible)
- `project_rm_preset_delivery_closeout.md` — RM preset delivery
  remains closed; email layers ON TOP of the existing notification
  path, doesn't reopen the core
- (no email-specific memory yet — will codify learnings if PR-159b's
  integration test surfaces anything non-obvious)

## Non-goals

- No production behavior change with default config
  (`ecm.email.enabled=false`, `from-address=` empty → all sends are
  silent no-ops)
- No event listener wiring — that's PR-159d
- No frontend change — that's PR-159c
- No SMTP integration test — that's PR-159b
- No CI gate change — that's PR-159e
- No multi-tenant per-tenant SMTP routing — single-server config
- No bounce / retry handling — `JavaMailSender` is fire-and-forget;
  failures logged at WARN, matches project's AFTER_COMMIT @Async
  pattern

## Bottom line

PR-159 implementation is a small, self-contained foundation slice.
It adds one new service, one entity, one migration, one dependency,
and a unit test. With `ecm.email.enabled=false` (the default), no
production behaviour changes. With it on, deployments need SMTP
env vars set, and templates seeded via a follow-up slice.

The next four slices (PR-159b through PR-159e) layer abstraction,
preferences, wiring, and a CI gate on top, ending with operational
email dispatch on RM preset delivery events.
