# Phase 1 - P34 Search Highlight Sanitization (Design)

Date: 2026-02-07

## Objective

Improve search result highlight quality and safety by sanitizing noisy/unsafe markup in highlight snippets before they are sent to frontend rendering.

## Motivation

Search highlights can contain mixed HTML fragments from indexed content. Rendering these directly leads to:

- noisy snippets (`img/script/style` remnants)
- inconsistent match field quality (fields that only contain non-meaningful markup)
- readability issues with very long snippets

## Scope

- Backend helper only:
  - sanitize highlight snippets
  - keep `<em>` emphasis markers
  - remove other tags and normalize whitespace
  - enforce max snippet length
- Add dedicated helper tests

## Design

File: `ecm-core/src/main/java/com/ecm/core/search/SearchHighlightHelper.java`

### Sanitization strategy

- preserve `<em>` and `</em>` using temporary tokens
- strip script/style blocks completely
- remove all remaining tags
- collapse whitespace and trim
- clamp snippet length to fixed max (`280`)

### Match fields behavior

- `resolveMatchFields(...)` now ignores fields whose sanitized snippet becomes empty

### Summary behavior

- `resolveHighlightSummary(...)` now:
  - skips empty sanitized candidates
  - returns cleaned/truncated snippets
  - keeps preferred summary field priority unchanged

## Test Design

File: `ecm-core/src/test/java/com/ecm/core/search/SearchHighlightHelperTest.java`

- validates unsafe tags are removed while `<em>` is preserved
- validates empty-after-sanitize fields are not reported as match fields
- validates long snippets are truncated

## Compatibility

- API schema unchanged.
- Consumers still receive HTML-compatible snippet, but safer and cleaner.

