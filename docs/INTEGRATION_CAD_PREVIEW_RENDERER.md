# CAD Preview Renderer Integration

This document describes how to wire Athena (ecm-core) to an external CAD render
service for DWG/DXF previews and thumbnails.

## Configuration

Set the following environment variables for `ecm-core`:

```
ECM_PREVIEW_CAD_ENABLED=true
ECM_PREVIEW_CAD_RENDER_URL=http://host.docker.internal:18002/api/v1/render/cad
ECM_PREVIEW_CAD_AUTH_TOKEN=
ECM_PREVIEW_CAD_TIMEOUT_MS=30000
```

Notes:
- `host.docker.internal` works on Docker Desktop (macOS/Windows). On Linux,
  use the host IP or a reachable service DNS name.
- If `ECM_PREVIEW_CAD_RENDER_URL` is empty, CAD preview falls back to a default
  thumbnail (no CAD render).

## Service contract

- Method: `POST`
- Path: `/api/v1/render/cad`
- Request: `multipart/form-data` with field `file`
- Response: `image/png`

## Verification steps

1) Upload a DWG/DXF document to Athena.
2) Call preview and thumbnail endpoints:

```
GET /api/v1/documents/{documentId}/preview
GET /api/v1/documents/{documentId}/thumbnail
```

Expected:
- `preview.supported=true`
- `thumbnail` returns PNG bytes
