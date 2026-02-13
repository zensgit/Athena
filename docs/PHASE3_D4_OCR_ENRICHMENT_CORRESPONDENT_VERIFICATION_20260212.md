# Phase 3 Day 4: OCR-Driven Correspondent Enrichment (Verification) — 2026-02-12

## Scope

Verify that correspondent enrichment:
- is gated behind a feature flag
- only assigns when a document has no correspondent
- does not break OCR completion flow

## Automated Verification (Unit Test)

Run the targeted unit test in a Maven container:

```bash
cd ecm-core
docker run --rm -v "$(pwd)":/workspace -w /workspace maven:3-eclipse-temurin-17 mvn -q test -Dtest=OcrQueueServiceTest
```

Expected:
- exit code `0`
- log line for enrichment similar to:
  - `OCR enrichment: matched correspondent 'ACME' for document ...`

## Manual Verification (Optional)

1. Enable OCR + enrichment in `.env` (local only):
   - `ECM_OCR_ENABLED=true`
   - `ECM_OCR_ENRICH_CORRESPONDENT_ENABLED=true`
2. Restart:

```bash
bash scripts/restart-ecm.sh
```

3. Ensure you have at least one correspondent with a match pattern that can match OCR text.
4. Upload a scanned PDF/image that contains that pattern.
5. Trigger OCR from the preview dialog (`Queue OCR`).
6. After OCR completes, confirm the document has the correspondent assigned (UI shows correspondent / backend node details).

