# Phase 4 Day 2 - MIME Normalization for `application/octet-stream` (2026-02-13)

## Problem

Some uploads arrive with a generic MIME type such as `application/octet-stream` (or similar).
When persisted as-is, this leads to:

1. **False UNSUPPORTED previews** for content that is actually previewable (PDF/images).
2. **Operator confusion** when "preview unsupported" is really just a metadata issue.

Common sources include:

- multipart uploads where the client omits `Content-Type`
- `.bin` filenames / unknown extensions
- mail ingestion attachments with missing or generic type

## Design / Approach

1. **Canonicalize MIME values**
   - lowercase
   - strip parameters (`; charset=...`)

2. **Only normalize when the detected type is generic**
   - `application/octet-stream`
   - `binary/octet-stream`
   - `application/x-empty`

3. **Conservative inference (small allowlist)**
   - **Magic bytes first**:
     - PDF (`%PDF-`)
     - PNG / JPEG / GIF / WebP
   - **Filename extension fallback** for the same allowlist only

4. **Explicit non-goal (for now): UTF-8 / text sniffing**
   - We intentionally avoid "looks like text/plain" heuristics to prevent misclassifying truly unknown binaries and to keep existing `.bin` E2E flows stable.

## Implementation Notes

Backend changes:

- `ecm-core/src/main/java/com/ecm/core/util/MimeTypeNormalizer.java`
  - Normalizes MIME and (only for generic types) infers previewable types via magic bytes / extension allowlist.
- `ecm-core/src/main/java/com/ecm/core/service/ContentService.java`
  - Added filename-aware overload: `detectMimeType(String contentId, String filename)`.
  - Passes filename into Tika via metadata, then normalizes the result via `MimeTypeNormalizer`.
- `ecm-core/src/main/java/com/ecm/core/pipeline/processor/ContentStorageProcessor.java`
  - Uses filename-aware MIME detection during initial content storage.
- `ecm-core/src/main/java/com/ecm/core/service/VersionService.java`
  - Uses filename-aware MIME detection when creating versions.

Tests:

- `ecm-core/src/test/java/com/ecm/core/util/MimeTypeNormalizerTest.java`

## Verification

### 1) Backend unit tests

```bash
cd ecm-core
mvn -q test
```

### 2) Upload a PDF as `.bin` (mislabeled octet-stream) and ensure preview works

Generate a deterministic PDF sample as `.bin` (from the Playwright fixture):

```bash
python3 - <<'PY'
import base64, re
from pathlib import Path

text = Path('ecm-frontend/e2e/fixtures/pdfSample.ts').read_text(encoding='utf-8')
m = re.search(r"PDF_SAMPLE_BASE64\\s*=\\s*`([^`]*)`;", text, re.S)
if not m:
    raise SystemExit("PDF_SAMPLE_BASE64 not found")

Path('tmp/mime-test.bin').write_bytes(base64.b64decode(m.group(1).strip().replace('\\n', '')))
print("wrote tmp/mime-test.bin")
PY
```

Run the core smoke script (no tokens are printed):

```bash
bash scripts/get-token.sh admin admin
ECM_API=http://localhost:7700 ECM_TOKEN_FILE=tmp/admin.access_token ECM_UPLOAD_FILE=tmp/mime-test.bin \
  bash scripts/smoke.sh
```

Expected outcome:

- Smoke run finishes successfully.
- PDF preview API returns `supported=true` for the uploaded `.bin` PDF.

### 3) Frontend regression gate subset (Playwright)

```bash
cd ecm-frontend
ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 \
  npx playwright test \
    e2e/ocr-queue-ui.spec.ts \
    e2e/pdf-preview.spec.ts \
    e2e/search-preview-status.spec.ts \
    --project=chromium
```

