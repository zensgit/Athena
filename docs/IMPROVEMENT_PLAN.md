# Athena ECM Improvement Plan (Design Reference, Implementation by Reuse/Rewrite)

> Sources for inspiration only: reference-projects/alfresco-community-repo (GPLv3), reference-projects/paperless-ngx (GPLv3). We reuse ideas, not code.

## Priorities (short sprints)
1) Search UX & filters  
2) Async preview/conversion + OCR fallback  
3) Rule engine (conditions/actions) on existing events  
4) Stability/health checks & retries

## 1) Search UX & Filters
- Backend:
  - Ensure `SearchRequest` supports mimeTypes, tags, categories, date range, size range; return highlights.
  - Add tags/categories fields to `NodeDocument` if missing for filters.
  - Highlighter: prefer content/name/description snippets.
- Frontend:
  - Expose filters (mime, date, size, tag, category) in Search dialog.
  - Render highlights in results (safe HTML for <em> tags).
  - Sorting by relevance/date/name/size; show file type chips.

## 2) Async Preview/Conversion + OCR
- Pipeline:
  - After upload: enqueue preview/text-extract jobs to RabbitMQ; return fast to client.
  - PreviewService: pluggable renderers (PDF/image/Office); store renditions under /var/ecm/content/renditions or object storage.
  - ConversionService: keep text extraction; if text is empty and mime is image/pdf, enqueue OCR.
  - OCR: integrate Tesseract or external OCR API; on success update Document.textContent and reindex.
- Index update: on preview/text ready, call `searchIndexService.updateDocument`.
- Retry/log: per-task status, limited retries, error log for failed items.

## 3) Rule Engine (Events → Conditions → Actions)
- Use existing Spring events (`NodeCreated/Updated/Deleted/Moved/Version*` with `@TransactionalEventListener(AFTER_COMMIT)`).
- Data model: rules with triggers (event + path/type), conditions (path/type/metadata/tag/category), actions (move/copy/tag/category/webhook/notify/convert).
- Execution: match rules on event → enqueue actions (async) → log success/fail; admin API to enable/disable and view logs.

## 4) Stability & Health
- Healthchecks: keep strict checks for ES/Redis/RabbitMQ; add Keycloak token acquisition check if needed.
- Retries: index update & preview/OCR jobs should have bounded retries and DLQ/failed-status logging.
- Logging: reduce noisy libs (PDFBox/JOD); structured logs around pipeline tasks and rule executions.

## Proposed Sprint Breakdown (fast iterations)
- Sprint A (UX): backend filters + highlights; frontend filters + highlight rendering.
- Sprint B (Async media): queue-based preview/text; OCR fallback; reindex on completion.
- Sprint C (Rules): rule model + matching + async actions + admin endpoints.
- Sprint D (Hardening): retries/DLQ for jobs, optional Keycloak health probe, logging polish.

## Notes on Licensing
- Alfresco/paperless-ngx are GPLv3; do not copy code. Use them as design references only.

