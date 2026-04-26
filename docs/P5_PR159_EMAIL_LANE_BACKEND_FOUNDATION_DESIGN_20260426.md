# P5 PR-159 — Email lane backend foundation (design preview)

## Date
2026-04-26

## Status
**Design only. No code in this PR.** This MD is staged in parallel
with PR-160/PR-161 so that once Phase 5 Mocked verifies green on
PR-161, implementation can start immediately. PR-159 retains its
original number from the handoff doc
(`P5_NOTIFICATION_LANE_HANDOFF_AND_EMAIL_LANE_ENTRY_20260426.md`)
even though chronologically it lands after PR-161.

## Scope

Backend-only. Adds the foundation for outbound email notifications:

- New dependency: `org.springframework.boot:spring-boot-starter-mail`
- New entity: `EmailTemplate` (subject + body, Thymeleaf-rendered)
- New repository: `EmailTemplateRepository`
- New service: `EmailNotificationService` (sends rendered email
  via `JavaMailSender`)
- New migration: `084-email-notification-foundation.xml`
- New configuration: `spring.mail.*` defaults in `application.yml`
  (overridable by env)
- New unit tests: `EmailNotificationServiceTest` with mock
  `JavaMailSender`

**Not in PR-159 scope** (deferred to subsequent slices):

- Controller surface — deferred to PR-159b or a follow-up
- Frontend toggle / UI — deferred (subsequent slice in 5-slice plan)
- Wiring delivery events to email — deferred (subsequent slice)
- CI gate — deferred (subsequent slice)
- `NotificationChannel` abstraction — deferred (will follow PR-159
  once concrete email service exists)

## Why backend-first

- Lets PR-159 land cleanly on its own merits (compile + unit test
  pass, no integration risk)
- The frontend already has `notifyBy.*` preference shape — no UI
  controls until later wiring
- The future `NotificationChannel` abstraction needs the email
  service to exist before it can dispatch to it
- Adding the dependency in `pom.xml` is a one-time touch; doing it
  in PR-159 means later slices don't have a build-config preamble

## Existing infrastructure surveyed

### Inbox notifications already work end-to-end

`RmReportPresetDeliveryService` publishes both success and failure
notifications via `ActivityService.postDirectNotificationActivity(...)`.
That call:

1. Saves an `Activity` row
2. Calls `NotificationInboxService.createDirectNotification(recipient, activity)`
3. The user sees it in their in-app inbox card

Two preference keys gate this:

- `org.athena.rm.reportPreset.delivery.notifyOnSuccess`
- `org.athena.rm.reportPreset.delivery.notifyOnFailure`

(Defined in `RmReportPresetDeliveryService:55-56`.)

### What's missing for email

- No `JavaMailSender` bean configured
- No `spring-boot-starter-mail` in `ecm-core/pom.xml`
- No template entity / repository / Thymeleaf rendering
- No outbound-email service (only `EmailIngestionService` for inbound
  EML/MSG parsing exists)

### What PR-159 deliberately does NOT touch

- `RmReportPresetDeliveryService` — wiring delivery events to email
  is a separate slice
- `EcmEventListener` — same reason
- `NotificationInboxService` — already works, no changes
- Frontend — no UI controls until preference wiring slice

## Architecture

### Entity layer

**`EmailTemplate`** (new entity):

```java
@Entity
@Table(name = "email_template")
@Getter @Setter @NoArgsConstructor
public class EmailTemplate extends BaseEntity {
    @Column(nullable = false, unique = true)
    private String key;             // e.g. "rm.report_preset.delivery.succeeded"

    @Column(nullable = false)
    private String subjectTemplate; // Thymeleaf source

    @Column(nullable = false, columnDefinition = "text")
    private String bodyTemplate;    // Thymeleaf source (HTML or plain)

    @Column(nullable = false)
    private boolean htmlBody;       // true=HTML body, false=plain text

    @Column(nullable = false)
    private String locale;          // "en", "zh-CN", or "default"

    @Column
    private String description;     // human-readable
}
```

**Composite uniqueness:** `(key, locale)` so per-locale variants are
addressable. If only `default` exists for a key, all locales fall
back to it.

### Repository

```java
public interface EmailTemplateRepository extends JpaRepository<EmailTemplate, UUID> {
    Optional<EmailTemplate> findByKeyAndLocale(String key, String locale);
    Optional<EmailTemplate> findFirstByKeyAndLocaleIn(String key, List<String> locales);
}
```

### Service layer

**`EmailNotificationService`** (new service):

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailNotificationService {

    private final EmailTemplateRepository templateRepository;
    private final JavaMailSender mailSender;
    private final TemplateEngine thymeleafEngine; // configured separately

    @Value("${ecm.email.enabled:false}")
    private boolean emailEnabled;

    @Value("${ecm.email.from-address}")
    private String fromAddress;

    @Async
    public void send(
        String templateKey,
        String toAddress,
        String preferredLocale,
        Map<String, Object> variables
    ) {
        if (!emailEnabled) {
            log.debug("Email disabled (ecm.email.enabled=false); skipping {}", templateKey);
            return;
        }
        if (toAddress == null || toAddress.isBlank()) {
            log.warn("send: no recipient for templateKey={}", templateKey);
            return;
        }
        EmailTemplate template = resolveTemplate(templateKey, preferredLocale);
        if (template == null) {
            log.warn("send: template not found key={} locale={}", templateKey, preferredLocale);
            return;
        }
        try {
            String subject = renderTemplate(template.getSubjectTemplate(), variables);
            String body = renderTemplate(template.getBodyTemplate(), variables);
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(toAddress);
            helper.setSubject(subject);
            helper.setText(body, template.isHtmlBody());
            mailSender.send(message);
            log.info("send: dispatched template={} to recipient (length-only) len={}", templateKey, body.length());
        } catch (Exception ex) {
            log.warn("send: failed to dispatch template={}: {}", templateKey, ex.getMessage(), ex);
        }
    }

    private EmailTemplate resolveTemplate(String key, String locale) {
        List<String> fallbacks = computeLocaleFallbacks(locale);
        return templateRepository.findFirstByKeyAndLocaleIn(key, fallbacks).orElse(null);
    }

    private List<String> computeLocaleFallbacks(String locale) {
        // exact → language → default
        // e.g. "zh-CN" → ["zh-CN", "zh", "default"]
        // e.g. "en" → ["en", "default"]
        // e.g. null/blank → ["default"]
        // Implementation TBD
    }

    private String renderTemplate(String source, Map<String, Object> vars) {
        Context ctx = new Context();
        if (vars != null) ctx.setVariables(vars);
        return thymeleafEngine.process(source, ctx);
    }
}
```

**Why `@Async`:** email send is a side effect, not in the request
critical path. The existing `EcmEventListener` pattern uses `@Async`
for downstream side effects; `EmailNotificationService.send` follows
that convention.

**Why `@Value("${ecm.email.enabled:false}")`:** feature flag default
OFF so the dependency lands without changing operational behavior
in the default Athena deployment. Enabling email requires explicit
opt-in via env or override.

### Configuration layer

`application.yml` additions:

```yaml
ecm:
  email:
    enabled: ${ECM_EMAIL_ENABLED:false}
    from-address: ${ECM_EMAIL_FROM:no-reply@athena.local}

spring:
  mail:
    host: ${SPRING_MAIL_HOST:localhost}
    port: ${SPRING_MAIL_PORT:25}
    username: ${SPRING_MAIL_USERNAME:}
    password: ${SPRING_MAIL_PASSWORD:}
    properties:
      mail:
        smtp:
          auth: ${SPRING_MAIL_SMTP_AUTH:false}
          starttls:
            enable: ${SPRING_MAIL_SMTP_STARTTLS:false}
        debug: ${SPRING_MAIL_DEBUG:false}
```

**Why explicit env vars rather than relying on Spring Boot's
auto-binding:** explicit envs are documentable in `docker-compose.yml`
and `kustomize/`, and the SMTP properties are commonly overridden at
deployment.

### Migration

`084-email-notification-foundation.xml`:

```xml
<changeSet id="084-1-create-email-template" author="athena">
  <createTable tableName="email_template">
    <column name="id" type="uuid"><constraints primaryKey="true" nullable="false"/></column>
    <column name="key" type="varchar(200)"><constraints nullable="false"/></column>
    <column name="subject_template" type="varchar(500)"><constraints nullable="false"/></column>
    <column name="body_template" type="text"><constraints nullable="false"/></column>
    <column name="html_body" type="boolean" defaultValueBoolean="true"><constraints nullable="false"/></column>
    <column name="locale" type="varchar(20)" defaultValue="default"><constraints nullable="false"/></column>
    <column name="description" type="varchar(500)"/>
    <column name="created_at" type="timestamp" defaultValueComputed="CURRENT_TIMESTAMP"/>
    <column name="updated_at" type="timestamp" defaultValueComputed="CURRENT_TIMESTAMP"/>
  </createTable>
  <addUniqueConstraint
    tableName="email_template"
    columnNames="key, locale"
    constraintName="uk_email_template_key_locale"/>
  <createIndex tableName="email_template" indexName="ix_email_template_key">
    <column name="key"/>
  </createIndex>
</changeSet>

<changeSet id="084-2-seed-default-templates" author="athena">
  <insert tableName="email_template">
    <column name="id" valueComputed="gen_random_uuid()"/>
    <column name="key" value="rm.report_preset.delivery.succeeded"/>
    <column name="subject_template" value="[Athena] RM report preset '${presetName}' delivery succeeded"/>
    <column name="body_template" valueComputed="'... seed body ...'"/>
    <column name="html_body" valueBoolean="false"/>
    <column name="locale" value="default"/>
    <column name="description" value="Notification when an RM report preset delivery completes successfully."/>
  </insert>
  <insert tableName="email_template">
    ... mirror for ".failed" ...
  </insert>
</changeSet>
```

**Why two changesets:** lets us seed templates idempotently in
tests via `--contexts seed-only` if needed, and keeps the schema
change reversible without dropping data.

### Dependency: `pom.xml`

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-mail</artifactId>
</dependency>
```

That's the only new compile-scope dependency. Spring Boot's
`MailSenderAutoConfiguration` activates only when `spring.mail.host`
is set (or any `spring.mail.*` is set), so this is safe to add even
when email is disabled.

### Thymeleaf engine

Use a string-based Thymeleaf engine (not the file-based one):

```java
@Configuration
public class EmailTemplateEngineConfiguration {
    @Bean(name = "emailTemplateEngine")
    public TemplateEngine emailTemplateEngine() {
        TemplateEngine engine = new TemplateEngine();
        engine.setTemplateResolver(new StringTemplateResolver());
        return engine;
    }
}
```

This bean is qualifier-isolated to avoid colliding with any other
Thymeleaf use in the project.

### Initial seed templates

Two templates seeded in migration:

| Key | Subject | Body shape |
|-----|---------|-----------|
| `rm.report_preset.delivery.succeeded` | `[Athena] RM report preset '${presetName}' delivery succeeded` | Plain text. Lists trigger type, target folder, filename, document ID, executionId. Closes with link to web inbox. |
| `rm.report_preset.delivery.failed` | `[Athena] RM report preset '${presetName}' delivery failed` | Plain text. Same context plus error message. |

Both use `default` locale — i18n variants come in a separate slice.

## Unit tests

`EmailNotificationServiceTest`:

| Test | Subject |
|------|---------|
| `sendsRenderedSubjectAndBody_whenEnabled` | Mocks `JavaMailSender`; verifies `MimeMessage` has expected from/to/subject/body |
| `skipsSilently_whenDisabled` | `ecm.email.enabled=false`; mailSender never called |
| `logsAndSwallows_whenSendThrows` | mailSender throws; service logs warn but does not bubble |
| `resolvesTemplateByLocale_withFallbacks` | Tests "zh-CN" → "zh" → "default" cascade |
| `dropsRecipient_whenBlank` | empty toAddress → no send, no NPE |
| `returnsEarly_whenTemplateNotFound` | unknown key → no send, warning logged |

`EmailTemplateRepositoryTest`:
- Schema round-trip (save + findByKeyAndLocale)
- Composite unique constraint enforcement

No integration test with a real SMTP server in PR-159 — that's PR-159b
or a CI-only smoke (matches PR-122 pattern).

## Files to create

| File | Type |
|------|------|
| `ecm-core/src/main/java/com/ecm/core/integration/email/notify/EmailTemplate.java` | entity |
| `ecm-core/src/main/java/com/ecm/core/integration/email/notify/EmailTemplateRepository.java` | repo |
| `ecm-core/src/main/java/com/ecm/core/integration/email/notify/EmailNotificationService.java` | service |
| `ecm-core/src/main/java/com/ecm/core/integration/email/notify/EmailTemplateEngineConfiguration.java` | config |
| `ecm-core/src/main/resources/db/changelog/changes/084-email-notification-foundation.xml` | migration |
| `ecm-core/src/test/java/com/ecm/core/integration/email/notify/EmailNotificationServiceTest.java` | unit test |
| `ecm-core/src/test/java/com/ecm/core/integration/email/notify/EmailTemplateRepositoryTest.java` | unit test |

## Files to modify

| File | Change |
|------|--------|
| `ecm-core/pom.xml` | add `spring-boot-starter-mail` dependency |
| `ecm-core/src/main/resources/application.yml` | add `ecm.email.*` and `spring.mail.*` env-bound config |
| `ecm-core/src/main/resources/db/changelog/db.changelog-master.xml` | include `084-email-notification-foundation.xml` |

No existing service / event / controller code touched.

## Why this is small

- One service class (~80 lines)
- One entity (~30 lines)
- One repo interface
- One config class
- One migration with 2 seed inserts
- One Thymeleaf engine bean
- Two test classes
- No frontend, no controller, no event listener wiring

Total ~400 lines added across ~10 files. Reversible: revert the
commit and the system reverts to inbox-only notifications. The
migration is forward-compatible (the table can be dropped if email
is permanently abandoned, with no impact on inbox notifications).

## Security considerations

- `from-address` is server-configured, not user-configurable
- No SMTP secrets land in the repo (env-only via `${SPRING_MAIL_USERNAME}`)
- Template variables are HTML-escaped by Thymeleaf when `htmlBody=true`;
  plain-text mode is escape-irrelevant
- No reply-to from arbitrary user input — first iteration uses
  `from-address` for both From and Reply-To
- Default `ecm.email.enabled=false` so the dependency adds no
  operational surface in default deployments

## Verification plan

### Local
- `./mvnw clean test -pl ecm-core` (delegates Maven to Docker — local
  runs may need a working Docker; this caveat is the same as the rest
  of the project)
- Backend build clean
- Migration smoke: run Liquibase against an empty schema, verify
  `email_template` table exists with composite unique constraint
- Unit tests pass

### CI
- Phase 5 Mocked Regression Gate — unchanged (no e2e change in PR-159)
- Backend Verify — runs unit tests including the new ones
- Acceptance Smoke — unchanged (no UI change)

### Subsequent slice verification gates
- PR-159b: introduce one failing-path integration test (real SMTP via
  Greenmail or testcontainers) — gated only after PR-159 lands
- Wiring slice: e2e smoke that verifies an email arrives at a mock
  SMTP endpoint after a delivery event is published
- CI gate slice: dedicated `Email Channel Gate` job in CI

## Sequencing within email lane

| Slice | Subject | Lines | Deps |
|-------|---------|------:|------|
| **PR-159 (this slice)** | EmailNotificationService foundation | ~400 | spring-boot-starter-mail |
| PR-159b (next) | NotificationChannel abstraction (InboxChannel + EmailChannel) | ~250 | PR-159 |
| PR-159c | `notifyByEmail` preference + UI toggle on RM preset card | ~300 | PR-159b |
| PR-159d | Wire delivery events into email channel + e2e smoke | ~400 | PR-159c |
| PR-159e | CI: isolated Email Channel Gate (preflight + live gate) | ~150 | PR-159d |

Each slice is independently revertible. Total estimate 4-6 days
across the 5 slices, matching the original handoff plan.

## Memory entries that apply

- `project_rm_preset_delivery_closeout.md` — RM preset delivery core
  is closed; email layers ON TOP of the existing notification path,
  doesn't reopen the core
- `feedback_diagnostic_cadence_for_opaque_500s.md` — same diagnostic
  pattern applies if email-send has surprises
- (no specific email-related memory yet — one will be written when
  PR-159 lands and codifies any non-obvious learnings)

## Non-goals

- No `EcmEventListener` change in PR-159 — wiring is a later slice
- No frontend change — UI controls are a later slice
- No SMTP integration test — that's a later slice
- No CI gate change — that's a later slice
- No `NotificationChannel` abstraction yet — comes after PR-159 once
  the concrete `EmailNotificationService` exists
- No multi-tenant per-tenant SMTP routing — single-server config in v1
- No bounce/retry handling — `JavaMailSender` is fire-and-forget;
  failure is logged, not retried (matches the AFTER_COMMIT @Async
  pattern used elsewhere)

## Bottom line

PR-159 is a small, well-scoped foundation slice. It adds a single
service, a single entity, a migration, and one dependency. It does
not touch any production behavior — `ecm.email.enabled=false` by
default, and no caller exists yet.

The next four slices (PR-159b through PR-159e) layer abstraction,
preferences, wiring, and a CI gate on top, ending with operational
email dispatch on RM preset delivery events.

This MD is a design preview — implementation kicks off after PR-161
lands a green Phase 5 Mocked verdict.
