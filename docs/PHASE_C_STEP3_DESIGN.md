# Phase C Step 3 Design: Scheduled Rule Audit Verification

## Goal
Validate that scheduled rule executions emit audit logs and that rule execution analytics are reachable.

## Scope
- Extend the scheduled rule smoke test to confirm audit log entries after manual trigger.
- Confirm the rule execution summary endpoint responds with expected fields.

## Implementation
- Update `scripts/smoke.sh` scheduled rule block to:
  - Poll `/api/v1/analytics/rules/recent` for entries containing the scheduled rule name.
  - Fail if the scheduled rule successfully applied a tag but no audit entry appears.
  - Call `/api/v1/analytics/rules/summary?days=1` to verify summary payload.

## Success Criteria
- Scheduled rule trigger succeeds and applies the expected tag.
- Analytics recent rule activity includes the scheduled rule name.
- Rule execution summary endpoint responds with `executed` count field.

## Risks / Mitigations
- **Async delays**: Use retry loop with short sleeps before concluding failure.
- **Missing analytics permissions**: Smoke test requires admin token.

## Rollback
- Revert the scheduled rule audit verification block in `scripts/smoke.sh`.
