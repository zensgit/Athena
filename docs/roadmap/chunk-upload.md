# Chunked Upload & Resume

## Motivation
Support large files with resume on failure.

## Design
- Initiate upload -> session ID -> chunk POSTs -> finalize.
- Backend tracks offsets and SHA256; S3 multipart when enabled.
- Frontend uses parallel chunking with retry/backoff.

## API Sketch
- `POST /uploads` -> `{sessionId}`
- `PUT /uploads/{sessionId}` with `Content-Range`
- `POST /uploads/{sessionId}/complete`

## Test Plan
Simulate network errors; verify integrity and resumability.

