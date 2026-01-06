# Verification: Share Link Allowed IP API Validation (2026-01-06)

## Scope
- API returns HTTP 400 for invalid `allowedIps` inputs.

## Test Command
- `cd ecm-core && mvn -Dtest=ShareLinkControllerValidationTest test`

## Result
- ✅ Passed (1 test, 0 failures)
