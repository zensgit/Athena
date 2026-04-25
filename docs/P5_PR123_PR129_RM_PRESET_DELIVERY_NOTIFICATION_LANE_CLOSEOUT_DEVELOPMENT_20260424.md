# P5 PR-123..PR-129 RM Preset Delivery Notification Lane Closeout Development

## Scope

This closeout covers the owner-inbox notification lane added after the `PR-95..PR-122` RM preset delivery/operator milestone.

It does not rewrite `PR-122`; it records the new capability added on top of that milestone.

## Delivered Capability

- failed scheduled preset deliveries publish owner inbox notifications
- successful scheduled preset deliveries publish owner inbox notifications
- notification cards reuse the existing activity and notification inbox chain
- success and failure notifications can be muted independently with owner preferences
- RM page exposes the two preference toggles through the existing People preferences API
- disabled-preference full-stack smoke tests have been added to the Playwright suite
- notification publication failures are isolated from delivery execution ledger status
- the due-delivery trigger endpoint is documented and audited as an admin/ops trigger
- a Spring Security MVC test guards the trigger endpoint against non-admin access
- the consolidated notification acceptance gate is attached to the existing CI `frontend_e2e_core` stack

## Design Boundaries

- owner-scoped inbox notifications are in scope
- email delivery remains out of scope
- webhooks remain out of scope
- cross-owner delegation remains out of scope
- per-preset notification overrides remain out of scope
- no new notification table or delivery queue was introduced

## Important Historical Boundary

`PR-122` closed the scheduled delivery/operator milestone through `PR-121` and listed alerting as deferred.

`PR-123..PR-129` intentionally extend that historical boundary by adding owner-inbox alerting, preferences, validation hooks, and ops hardening. Future readers should treat this closeout as the notification-lane continuation, not as a correction to the older milestone.

## Remaining Work

- observe the GitHub Actions `frontend_e2e_core` run after the CI gate wiring lands
- confirm the `Run RM notification acceptance gate` step passes in CI
- capture run id, commit SHA, and the gate completion evidence described in `P5_PR139_RM_NOTIFICATION_CI_OBSERVATION_CLOSEOUT_READINESS_DESIGN_20260425.md`
- only after that pass, mark the notification lane fully accepted
