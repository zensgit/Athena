# Design: RuleAction Notification Type Overload (2026-01-06)

## Goal
Expose a RuleAction factory overload that can include notification type metadata.

## Scope
- RuleAction factory methods only.
- No runtime behavior changes beyond parameter mapping.

## Approach
- Add `sendNotification(recipient, message, type)` overload.
- Default `sendNotification(recipient, message)` delegates to the overload.
- Only set `type` param when non-blank.

## Files
- `ecm-core/src/main/java/com/ecm/core/entity/RuleAction.java`
- `ecm-core/src/test/java/com/ecm/core/entity/RuleActionTest.java`
