# Object Storage Integration

## Motivation
Offload binary content to S3/MinIO; improve scalability and durability.

## Design
- Store metadata (DB) separate from content (S3 bucket).
- Content key: `nodes/{uuid}/v{version}/{sha256}`.
- Spring profile `s3` with credentials via env.
- Migrate existing files via batch job.

## API/Model
- No breaking API; storage abstraction behind `ContentService`.

## Risks/Rollback
- Dual-write guard; toggle via feature flag.

## Test Plan
- Unit for service adapter; integration with local MinIO; migration dry runs.

