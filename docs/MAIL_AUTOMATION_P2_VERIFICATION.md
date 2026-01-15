# Mail Automation P2 Verification

## Automated Tests
- `cd ecm-core && mvn test`
  - Result: pass

## Manual Checks (Not Run)
- Verify scheduler respects per-account poll interval by observing logs/metrics.
- Verify `POST /api/v1/integration/mail/fetch` bypasses the interval gate.
- Inspect `/actuator/metrics/mail_fetch_accounts_total` and related timers if metrics are exposed.
