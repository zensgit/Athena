# P5 RM Preset Delivery Notification Remaining Work

## Current Capability Envelope

The current local worktree covers the owner-scoped inbox notification lane for RM report preset scheduled delivery:

- failed scheduled deliveries publish owner inbox notifications
- successful scheduled deliveries publish owner inbox notifications
- both notification types reuse the existing activity and notification inbox chain
- owner preferences can mute success and failure inbox notifications independently
- RM page exposes the two preference toggles through the existing People preferences API
- full-stack smoke coverage exists for success and failure notification paths
- disabled-preference full-stack smoke tests are added and discoverable, with live execution pending
- notification publication failures are isolated from delivery execution ledger status
- the due-delivery trigger endpoint is documented, audited, and security-tested as an admin/ops trigger
- one repeatable acceptance gate now covers five backend notification tests plus four tagged full-stack notification Playwright flows
- the acceptance gate is wired into the existing `frontend_e2e_core` CI job after stack and Keycloak realm readiness
- the gate readiness checks now use bounded retries for backend health, Keycloak discovery, and UI reachability

## Completion Assessment

The product surface is close to complete for the scoped target: scheduled RM report preset delivery with owner inbox notification controls.

The remaining must-have work is CI observation. It is not another large product-build slice unless the target expands to email/webhook delivery.

## Must-Have Remaining Work

| Item | Reason | Estimated Effort |
| --- | --- | --- |
| Observe the CI `frontend_e2e_core` run with the new RM notification gate | local Docker socket was unavailable, so GitHub Actions is the authoritative full-stack runner | 0.5 day |
| Promote closeout from development-complete to accepted | closeout exists, but it intentionally keeps final acceptance pending until the CI gate passes | 0.25 day |

Estimated must-have effort: 0.75 day.

## Not Required For This Target

These are separate capability decisions, not blockers for the current owner-inbox notification target:

- email delivery channel
- webhook delivery channel
- cross-owner/delegated delivery policy
- per-preset notification preference overrides
- SLO alerting and escalation policy
- downloadable bundle delivery

## Recommended Next Slice

Finish `PR-132` CI validation first:

1. Push the CI wiring and let `frontend_e2e_core` run.
2. Confirm the `Run RM notification acceptance gate` step executes the five backend targeted tests and four tagged Playwright flows.
3. If green, update the notification-lane closeout verification status from pending to accepted.

Do not start email/webhook work until the current owner-inbox lane is fully closed, otherwise the verification boundary will blur.
