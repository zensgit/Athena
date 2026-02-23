# Phase 88: Phase5 Regression Hotspot Summary

## Date
2026-02-21

## Background
- `phase5-regression` reported pass/fail only; operators lacked run-time hotspots and flaky-risk visibility.

## Goal
1. Add per-run timing hotspot summary.
2. Add heuristic flaky-risk candidate list for faster triage.

## Changes

### 1) Timing summary extractor
- File: `scripts/phase5-regression.sh`
- Added `print_playwright_timing_summary(log_file)`:
  - parses Playwright console result lines
  - prints top 5 slowest mocked specs (duration hotspots)

### 2) Flaky-risk heuristic summary
- File: `scripts/phase5-regression.sh`
- Added heuristic ranking:
  - duration thresholds
  - dynamic-flow keyword weighting (`watchdog`, `auth`, `session`, `mail`, etc.)
- Outputs top 3 “flaky-risk candidates” for operator awareness.

### 3) Integration point
- Timing summary now prints after Playwright run, before pass/fail branch exit.

## Impact
- No test behavior change.
- Improves post-run triage signal without adding external dependencies.
