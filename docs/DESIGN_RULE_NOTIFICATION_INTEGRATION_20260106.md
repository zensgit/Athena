# Design: Rule SEND_NOTIFICATION Integration (2026-01-06)

## Goal
Route rule-based SEND_NOTIFICATION actions through the existing NotificationService instead of logging only.

## Scope
- RuleEngineService SEND_NOTIFICATION action.
- No API contract changes.

## Approach
- Resolve recipient/message from action params.
- Expand `{documentName}` and `{documentId}` placeholders.
- Use NotificationService `notifyUser(recipient, title, message)`.
- If `type` is provided, include it in the title for visibility.

## Files
- `ecm-core/src/main/java/com/ecm/core/service/RuleEngineService.java`
- `ecm-core/src/test/java/com/ecm/core/service/RuleEngineServiceNotificationTest.java`
