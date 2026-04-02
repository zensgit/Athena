# Phase 368Y — Discovery API

> **Scope**: `/api/v1/discovery` — repository version, build, modules, capabilities, status, metrics
> **Date**: 2026-03-30

---

## 1. Problem Statement

No programmatic way to discover what version of Athena is running, what modules are installed,
or what capabilities the repository supports. Alfresco exposes this via
`GET /api/-default-/public/alfresco/versions/1/discovery`. Clients (mobile apps, integrations,
health checks) need this to adapt behavior.

## 2. Endpoint

```
GET /api/v1/discovery
GET /api/discovery
```

No authentication required for basic info (version/capabilities). Metrics may be restricted in future.

## 3. Response Shape

```json
{
  "repository": {
    "id": "ecm-core",
    "edition": "Community",
    "version": {
      "display": "2.5.0",
      "buildNumber": "1234",
      "buildDate": "2026-03-30"
    },
    "modules": [
      { "id": "ecm-core", "title": "ECM Core Repository", "version": "2.5.0" },
      { "id": "ecm-frontend", "title": "ECM Frontend SPA", "version": "2.5.0" },
      { "id": "ecm-search", "title": "Elasticsearch Integration", "version": "2.5.0" },
      { "id": "ecm-preview", "title": "Document Preview Pipeline", "version": "2.5.0" },
      { "id": "ecm-workflow", "title": "Flowable BPM Integration", "version": "2.5.0" },
      { "id": "ecm-rules", "title": "Automation Rule Engine", "version": "2.5.0" },
      { "id": "ecm-wopi", "title": "WOPI / Collabora Integration", "version": "2.5.0" },
      { "id": "ecm-ocr", "title": "Tesseract OCR Pipeline", "version": "2.5.0" },
      { "id": "ecm-mail", "title": "IMAP/OAuth Mail Ingestion", "version": "2.5.0" },
      { "id": "ecm-odoo", "title": "Odoo ERP Integration", "version": "2.5.0" },
      { "id": "ecm-virus", "title": "ClamAV Virus Scanning", "version": "2.5.0" },
      { "id": "ecm-ml", "title": "ML Classification Service", "version": "2.5.0" }
    ],
    "capabilities": [
      "versioning", "checkout", "working-copy", "locking", "lock-types",
      "permissions", "permission-templates", "search", "faceted-search",
      "full-text-search", "preview", "renditions", "workflow", "automation-rules",
      "comments", "nested-comments", "tags", "categories", "favorites",
      "share-links", "associations", "secondary-children", "content-models",
      "aspects", "property-constraints", "audit-log", "batch-download",
      "webhooks", "webdav", "wopi", "ocr", "virus-scan", "ml-classification",
      "barcode-extraction", "pdf-annotations", "mail-ingestion",
      "erp-integration", "multi-language"
    ],
    "status": {
      "state": "RUNNING",
      "timestamp": "2026-03-30T14:30:00Z"
    },
    "metrics": {
      "activeContentModels": 3
    }
  }
}
```

## 4. Configuration

```yaml
ecm:
  version: 2.5.0
  edition: Community
  build:
    number: ${BUILD_NUMBER:dev}
    date: ${BUILD_DATE:}
```

All values have sensible defaults; `buildDate` is blank in dev.

## 5. Files Created

| File | Purpose |
|------|---------|
| `controller/DiscoveryController.java` | REST endpoint + response records |
| `test/controller/DiscoveryControllerTest2.java` | 6 focused tests |

## 6. NOT Modified

All preview/rendition/search/ops-governance files untouched. No DB migration needed.
