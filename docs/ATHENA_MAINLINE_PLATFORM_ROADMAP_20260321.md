# Athena Mainline Platform Roadmap

## Scope
This document sets the mainline strategic direction for Athena as of 2026-03-21.
It is intended to keep product and implementation work aligned while the codebase
is already carrying a broad set of capabilities.

## Benchmark References
- Primary benchmark: `reference-projects/alfresco-community-repo`
- Secondary reference: `reference-projects/paperless-ngx`

Alfresco is the main parity and surpass target for enterprise ECM behaviors.
Paperless-ngx is the main reference for pipeline-oriented automation and UX
patterns around document processing.

## Current Assessment
Athena is no longer a narrow feature scaffold. It already has broad coverage in:
- search: facets, highlight, spellcheck, suggestions, advanced stats, preview-status aware search
- workflow: process browser, task browser, history, variables, involved actors, activity timeline
- preview: queueing, failure classification, dead-letter handling, retry, failover, diagnostics
- batch/export: async task centers, cancel/cleanup/download flows, governance summaries

The main gap is not surface area. The main gap is platform coherence: multiple
capabilities are already implemented, but the control plane and domain contracts
are still fragmented across controllers and page-specific behavior.

## Revised Priority Order
1. Unified async task control plane
2. First-class rendition resource model
3. Unified search contract and DSL
4. Config-driven security and pipeline productization

## What To Stop Over-Investing In
- Do not keep expanding isolated UI parity work unless it clearly feeds a shared contract.
- Do not add more one-off async task endpoints if the new behavior can be expressed through a shared task framework.
- Do not keep growing controller size as the default integration pattern.
- Do not prioritize page-level polish over reusable domain primitives.
- Do not treat workflow/search/preview/batch as separate vertical silos when the user intent is operational governance.

## Next Executable Phases
### Phase 1: Unified Async Task Control Plane
Build a shared async task model for audit, ops, search, preview, and batch download.
Minimum contract:
- create
- status
- list
- summary
- cancel
- cleanup
- download when applicable
- accepted response with clear polling hint

Target outcome:
- all async centers expose the same lifecycle semantics
- admin dashboards can aggregate task health across domains
- retry and cancellation policies become reusable instead of duplicated

### Phase 2: First-Class Rendition Resource Model
Promote rendition from a derived preview state into an explicit resource model.
Focus on:
- stable resource identity
- readiness state
- stale/invalidated state
- failure reason and category
- repair / requeue / export actions

Target outcome:
- preview, thumbnail, and related rendition actions are treated as resources
- preview diagnostics becomes a consumer of a shared rendition model, not the owner of bespoke logic

### Phase 3: Unified Search Contract and DSL
Consolidate search around a single request/response model for:
- query
- filters
- stats
- pivot views
- explainability
- dry-run export

Target outcome:
- search, preview retry scope, and governance reporting share the same filtering semantics
- advanced search becomes a platform query API instead of a page-specific feature set

### Phase 4: Config-Driven Security and Pipeline Productization
Move toward configurable policy and processing layers instead of hard-coded enumerations and fixed processor assumptions.
Focus on:
- permission definitions and roles as data
- pipeline registration and observability
- processor capability metadata
- policy-driven gating for automation and remediation

Target outcome:
- Athena becomes easier to extend without editing core control code for every new rule or processor
- platform behavior is more declarative and less coupled to specific controller implementations

## Operating Principle
When choosing the next task, prefer work that:
- reduces duplication across task centers
- creates a reusable domain contract
- improves governance, observability, or recovery
- raises the platform ceiling instead of only adding another screen

If a change only improves one page but does not strengthen a shared model, it should
be treated as lower priority.
