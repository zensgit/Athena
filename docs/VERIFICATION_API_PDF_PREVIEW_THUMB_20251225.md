# API PDF Preview/Thumbnail Verification (2025-12-25)

## Environment
- Backend: http://localhost:7700
- Keycloak: http://localhost:8180
- Folder: uploads/pdf-batch-20251225_144551 (`7979477f-429e-4d4f-b47f-3c6f6f701b13`)

## Steps
1. Enumerate files in the batch folder.
2. For each document, call:
   - `GET /api/v1/documents/{id}/preview`
   - `GET /api/v1/documents/{id}/thumbnail`
3. Record HTTP status, preview support, page count, and thumbnail byte size.

## Results
| File | Preview HTTP | Supported | Pages | Thumbnail HTTP | Thumbnail Bytes |
| --- | --- | --- | --- | --- | --- |
| 改图28.pdf | 200 | true | 1 | 200 | 16902 |
| 简易三坐标数控机床设计.pdf | 200 | true | 53 | 200 | 4260 |
| 3-291-249-885-00_RevB1_mx.pdf | 200 | true | 1 | 200 | 11792 |
| 3-752-062-962-00_RevB2_mx.pdf | 200 | true | 1 | 200 | 12529 |
| 3-752-063-959-00_RevC1_mx.pdf | 200 | true | 1 | 200 | 12400 |
| 1-490-031-395-00_RevD.PDF | 200 | true | 1 | 200 | 10830 |

## Artifacts
- Log: `tmp/pdf-batch-preview-thumb-20251225_151248.tsv`

## Conclusion
All preview and thumbnail endpoints returned HTTP 200 with supported previews and non-empty thumbnail payloads.

## Cleanup
- `pdf-batch-20251225_144551` was no longer present (404 on fetch/delete).
- Removed leftover batch folder `pdf-batch-20251225_144510` (ID `91df9b26-9741-47c7-95de-351e6a9129fc`), delete status 204.
