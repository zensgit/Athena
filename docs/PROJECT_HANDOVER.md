# Athena ECM - Project Handover Document

> **Date**: 2025-12-10
> **Version**: 1.0.0 (Release)
> **Status**: Ready for Deployment

## 1. Project Overview

Athena ECM is an enterprise-grade Content Management System built with a modern tech stack. It features intelligent document processing, business process management, and seamless external integrations.

### Key Capabilities
*   **Document Management**: Versioning, Metadata, Taxonomy, Permissions.
*   **Intelligent Pipeline**: Auto-OCR, ML Classification, Rule Engine.
*   **Workflow**: Document Approval processes via Flowable.
*   **Search**: Full-text and Faceted search via Elasticsearch.
*   **Online Editing**: Integrated with WPS / Office Online.
*   **Integrations**: Odoo ERP, Email Archiving.

## 2. Technical Stack

| Component | Technology | Description |
|-----------|------------|-------------|
| **Backend** | Java 17, Spring Boot 3.2 | Core REST API, Business Logic |
| **Frontend**| React 18, TypeScript, MUI | User Interface |
| **Database**| PostgreSQL 15 | Metadata, Audit Logs |
| **Search**  | Elasticsearch 8.11 | Search Engine |
| **Process** | Flowable 7.0 | Workflow Engine |
| **Storage** | MinIO | S3-compatible Object Storage |
| **ML**      | Python 3.11, FastAPI | Classification Service |
| **Queue**   | RabbitMQ | Event Bus |

## 3. Deployment Guide

### Prerequisites
*   Docker & Docker Compose installed.
*   4GB+ RAM available.

### One-Click Start
The entire stack is configured in `docker-compose.yml`.

```bash
# 1. Clone repository
git clone <repo-url>
cd Athena

# 2. Start services (background)
docker-compose up -d

# 3. Verify services
docker-compose ps
```

### Access Points
*   **Frontend**: [http://localhost:5500](http://localhost:5500)
*   **Backend API**: [http://localhost:7700/api/v1](http://localhost:7700/api/v1)
*   **Swagger Docs**: [http://localhost:7700/swagger-ui.html](http://localhost:7700/swagger-ui.html)
*   **Keycloak**: [http://localhost:8180](http://localhost:8180)
*   **MinIO Console**: [http://localhost:9206](http://localhost:9206) (User: minio_access_key, Pass: minio_secret_key)

## 4. Development Guide

### Directory Structure
*   `ecm-core/`: Main Java Backend application.
*   `ecm-frontend/`: React Frontend application.
*   `ml-service/`: Python Machine Learning service.
*   `docs/`: Detailed design documents and sprint reports.

### Key Configuration
*   **Environment Variables**: Managed in `.env` file at root.
*   **Backend Config**: `ecm-core/src/main/resources/application.yml`
*   **WPS/WOPI Config**: Update `ecm.wps.*` properties in application.yml for production usage.

## 5. Feature Verification (Smoke Test)

1.  **Upload**: Go to Frontend -> Browse -> Click "Upload". Upload a PDF.
2.  **Search**: Type filename in the search bar. Check facets on the left.
3.  **Edit**: In the file list, click the "Edit" (Pencil) icon. Verify Editor opens.
4.  **Workflow**: (API only currently) Use Swagger to `POST /api/v1/workflows/document/{id}/approval`.

## 5.1 Ops Notes (Storage Permissions)

- If uploads fail with `Content storage failed: /var/ecm/content/...`, the storage volume is likely owned by the wrong user.
- The `ecm-core` container startup now fixes ownership for `/var/ecm/content`, but legacy volumes may still require a one-time fix:
  - `docker exec -u 0 athena-ecm-core-1 chown -R app:app /var/ecm/content`

## 6. Known Limitations & Next Steps

*   **WPS Signature**: The signature verification in `WpsController` is currently simplified. For production, implement the full HMAC-SHA256 signature check required by WPS open platform.
*   **Frontend Workflow**: The UI for "My Tasks" and "Start Workflow" is not yet implemented (Backend APIs are ready).
*   **Load Balancing**: Current setup is single-instance. Nginx is configured as a simple proxy.

---
**Thank you for developing with Athena ECM!**
