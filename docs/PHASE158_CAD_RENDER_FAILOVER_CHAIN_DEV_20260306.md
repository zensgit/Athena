# Phase158 Dev: CAD Render Failover Chain

## Date
2026-03-06

## Goal
Borrow Alfresco failover-transform pattern and apply it to Athena CAD render requests so a single render endpoint outage does not block preview generation.

## Reference pattern
- Alfresco: `org/alfresco/repo/content/transform/LocalFailoverTransform.java`
- Core idea: iterate engines in sequence and stop on first success.

## Athena implementation
- File: `ecm-core/src/main/java/com/ecm/core/preview/PreviewService.java`
- Added config:
  - `ecm.preview.cad.render-fallback-urls` (comma/semicolon/newline separated)
- Added URL resolver:
  - deduplicates and preserves order: primary URL first, then fallbacks.
- Updated CAD request flow:
  - try each endpoint sequentially
  - record first exception and continue to next endpoint
  - return success immediately on first successful response
  - preserve retry-hint header behavior on each endpoint

## Operational effect
- CAD preview path is resilient to single-endpoint failures.
- Existing configuration remains backward compatible (no fallback URLs required).
