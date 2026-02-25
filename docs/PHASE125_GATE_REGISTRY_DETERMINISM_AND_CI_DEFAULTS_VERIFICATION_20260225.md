# Phase 125 Verification: Gate Registry Determinism and CI Defaults

## Date
2026-02-25

## Scope
- Verify script syntax after CI-default and DIFF parser changes.
- Verify sync helper passes with idempotence check enabled.
- Verify strict mocked delivery gate remains green with new diagnostics output.

## Verification Commands
1. `bash -n scripts/phase5-regression.sh`
2. `bash -n scripts/phase5-phase6-delivery-gate.sh`
3. `bash -n scripts/phase5-sync-recovery-registry.sh`
4. `scripts/phase5-sync-recovery-registry.sh`
5. `CI=1 DELIVERY_GATE_MODE=invalid bash scripts/phase5-phase6-delivery-gate.sh` (startup probe)
6. `PHASE5_RECOVERY_GUARD_STRICT=1 PHASE5_RECOVERY_REGISTRY_STRICT=1 DELIVERY_GATE_MODE=mocked DELIVERY_GATE_RECOVERY_REGISTRY_SYNC=1 PW_WORKERS=1 bash scripts/phase5-phase6-delivery-gate.sh`

## Results
- Syntax checks:
  - PASS (`phase5-regression.sh`, `phase5-phase6-delivery-gate.sh`, `phase5-sync-recovery-registry.sh`)
- Sync helper:
  - PASS
  - includes deterministic check output:
    - `phase5_sync_recovery_registry: deterministic check passed`
  - registry validation output includes DIFF lines with zero mismatch:
    - `DIFF missing_from_events_file_csv: none`
    - `DIFF stale_events_file_entries_csv: none`
- CI default startup probe:
  - PASS (expected fast-fail due to invalid mode)
  - startup output confirms:
    - `DELIVERY_GATE_RECOVERY_REGISTRY_SYNC=1`
    - `DELIVERY_GATE_RECOVERY_REGISTRY_SYNC_SOURCE=ci_default`
- Strict mocked delivery gate:
  - PASS
  - key output:
    - `30 passed (1.5m)`
    - `phase5_phase6_delivery_gate: ok`
    - fast layer:
      - `[PASS] mocked recovery registry preflight`
      - `[PASS] mocked regression gate`

## Conclusion
- Phase125 is verified.
- Registry sync behavior is more deterministic and CI-friendly, with improved mismatch diagnostics.
