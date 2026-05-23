# MailAutomationController Response-Contract Tests

Date: 2026-05-23

## Context

This slice continues the backend response-contract track after the
WorkflowController follow-up. The TODO identifies MailAutomationController as
the final unstarted Top 10 group, with high frontend traffic from
`MailAutomationPage`, `AdminDashboard`, and `mailAutomationService`.

## Scope

Added:

- `ecm-core/src/test/java/com/ecm/core/integration/mail/controller/MailAutomationControllerResponseContractTest.java`

Covered JSON endpoints:

- `GET /api/v1/integration/mail/accounts`
- `GET /api/v1/integration/mail/rules`
- `GET /api/v1/integration/mail/runtime-metrics`

Out of scope:

- Provider presets, already covered by dedicated preset tests.
- Diagnostics/report/processed-mail endpoints.
- OAuth callback/authorize endpoints.
- Fetch/preview/replay/cleanup/mutation endpoints.
- CSV export endpoints.
- Controller implementation changes.
- Frontend changes.

## Design

The test uses standalone `MockMvc` with mocked mail repositories/services. This
slice is a pure response-shape contract and does not need Spring Security
context; security coverage already exists in `MailAutomationControllerSecurityTest`.

The slice locks these wire DTOs:

- `MailAccountResponse`
- `MailRuleResponse`
- `MailFetcherService.MailRuntimeMetrics`
- `MailFetcherService.MailRuntimeErrorStat`
- `MailFetcherService.MailRuntimeTrend`

The tests lock:

- account OAuth fields and fetch-status fields as explicit JSON nulls for a
  password/SSL account;
- secret redaction booleans (`passwordConfigured`, `oauthEnvConfigured`,
  `oauthMissingEnvKeys`, `oauthConnected`);
- rule filter/action nullable fields as explicit JSON nulls;
- runtime metrics nullable fields (`avgDurationMs`, `lastErrorAt`) as explicit
  JSON nulls;
- nested runtime error-stat and trend field sets;
- runtime metrics audit side-effect for the effective `windowMinutes`.

## Verification

Local static hygiene:

```bash
git diff --check -- . ':!.env'
```

Result: passed.

Targeted Maven test:

```bash
cd ecm-core
./mvnw -Dtest=MailAutomationControllerResponseContractTest test
```

Result: blocked by the local environment before Maven startup:

```text
failed to connect to the docker API at unix:///Users/chouhua/.docker/run/docker.sock; check if the path is correct and if the daemon is running: dial unix /Users/chouhua/.docker/run/docker.sock: connect: no such file or directory
```

CI is the authoritative execution gate for this slice.

## CI Follow-Up

Final CI:

- GitHub Actions run: `26333415170`
- Head: `6bda32d5e0dc7d1123915bbd7bbebeae525a7740`
- Result: `success`

All seven jobs passed:

- Frontend Build & Test
- Backend Verify
- Phase C Security Verification
- Frontend E2E Core Gate
- Property Encryption Closeout Gate
- Acceptance Smoke (3 admin pages)
- Phase 5 Mocked Regression Gate
