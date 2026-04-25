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
- one repeatable acceptance gate now covers five backend notification test classes plus four tagged full-stack notification Playwright flows
- the acceptance gate is wired into the existing `frontend_e2e_core` CI job after stack and Keycloak realm readiness
- the gate readiness checks now use bounded retries for backend health, Keycloak discovery, and UI reachability
- the four notification acceptance flows force due state directly instead of waiting for near-future cron time
- CI failure artifacts include backend Surefire reports for the gate's targeted Maven tests
- Playwright API setup/trigger assertions in the notification spec now report request context, status, URL, and response body preview on failure
- People service unit tests now guard the single-preference get/set/delete endpoint contract used by RM notification toggles
- Records Management page tests now guard optimistic notification preference toggle rollback on failed preference saves
- Records Management page tests now cover rollback for both success and failure notification toggles
- PR-139 records the closeout evidence required before this lane can be marked accepted
- PR-141 adds a local closeout preflight script for all non-Docker, non-network checks before CI observation
- PR-142 hardens preflight discovery-count failures so missing acceptance tests produce an explicit diagnostic
- PR-143 aligns the integration doc with the default-on preference contract and tests missing preference values in the RM page
- PR-144 runs the closeout preflight in the non-Docker frontend CI job before slower gates
- PR-144A makes the closeout preflight verify CI workflow wiring semantics, including the live gate, fast preflight, Surefire artifacts, and critical step ordering

## Completion Assessment

The product surface is close to complete for the scoped target: scheduled RM report preset delivery with owner inbox notification controls.

The remaining must-have work is CI observation with a reachable GitHub Actions API or a CI run link supplied by a collaborator. It is not another large product-build slice unless the target expands to email/webhook delivery.

## Must-Have Remaining Work

| Item | Reason | Estimated Effort |
| --- | --- | --- |
| Observe the CI `frontend_e2e_core` run with the new RM notification gate | local Docker socket and GitHub Actions API access were unavailable here, so CI evidence must be captured outside this sandbox | 0.5 day |
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

Finish `PR-139` CI observation first:

1. Push the CI wiring/hardening if it is not already on the branch.
2. Run `scripts/p5-rm-notification-closeout-preflight.sh` locally.
3. Capture the GitHub Actions run id and commit SHA.
4. Confirm the `Run RM notification acceptance gate` step executes the five backend targeted test classes and four tagged Playwright flows.
5. If green, update the notification-lane closeout verification status from pending to accepted.

Do not start email/webhook work until the current owner-inbox lane is fully closed, otherwise the verification boundary will blur.
