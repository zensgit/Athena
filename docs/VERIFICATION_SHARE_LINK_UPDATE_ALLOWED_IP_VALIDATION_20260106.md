# Verification: Share Link Update Allowed IP Validation (2026-01-06)

## Scope
- HTTP 400 on invalid `allowedIps` during share-link update.

## Test Command
- `cd ecm-core && mvn -Dtest=ShareLinkControllerValidationTest test`

## Result
- ✅ Passed (2 tests, 0 failures)
