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
- PR-144B makes the closeout preflight verify the four specific notification acceptance scenarios, not only the tagged-test count
- PR-144C makes the closeout preflight verify the five backend test classes targeted by the live notification acceptance gate
- PR-144D removes the preflight's hidden `rg` dependency after GitHub Actions run `24935937705` failed on `rg: command not found`
- PR-149 isolates each scheduled preset in its own `REQUIRES_NEW` transaction through the Spring proxy
- PR-149B hardens the remaining optional notification boundaries: missing preference reads no longer mark caller transactions rollback-only, and direct owner notification activity/inbox publication runs in its own transaction
- PR-154 fixes the final Playwright strict-locator issue in the default-on success notification scenario
- PR-155 promotes the notification lane to accepted after GitHub Actions run `24947642547` passed `Run RM notification acceptance gate`

## Completion Assessment

The product surface is accepted for the scoped target: scheduled RM report preset delivery with owner inbox notification controls.

No must-have work remains in this lane. New channels such as email or webhook are separate product capabilities, not remaining notification-lane closeout work.

## Must-Have Remaining Work

| Item | Reason | Estimated Effort |
| --- | --- | --- |
| None | GitHub Actions run `24947642547` passed the required notification acceptance gate | 0 day |

Estimated must-have effort: 0 day.

## Not Required For This Target

These are separate capability decisions, not blockers for the current owner-inbox notification target:

- email delivery channel
- webhook delivery channel
- cross-owner/delegated delivery policy
- per-preset notification preference overrides
- SLO alerting and escalation policy
- downloadable bundle delivery

## Accepted Evidence

GitHub Actions run `24947642547` on commit `7f3cb44`:

- `Backend Verify`: success
- `Frontend Build & Test`: success
- `Phase C Security Verification`: success
- `Acceptance Smoke (3 admin pages)`: success
- `Run RM notification acceptance gate`: success
- `Run core E2E gate`: success

## Recommended Next Slice

The owner-inbox notification lane is closed. The next code slice should address the visible Phase 5 Mocked Regression Gate failures before opening the email delivery channel, because Phase 5 Mocked remains the active CI-red lane while email would add a new capability and broaden the verification boundary.
