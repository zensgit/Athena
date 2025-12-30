# Requirements: Upload & Processing Pipeline (2025-12-28)

## Scope
File uploads, processing stages, antivirus, and error handling.

## Requirements
1. **Upload progress**
   - Show progress for large files with cancel/retry.
   - Prevent duplicate submissions on slow networks.

2. **Validation and limits**
   - Clear error messages for size/type limits.
   - Expose max file size in UI settings/help.

3. **Antivirus feedback**
   - If a file is rejected, show a clear reason (e.g., virus detected).
   - Log rejection in audit trail.

4. **Conversion status**
   - For previewable files, show processing state (queued/processing/ready).
   - Provide fallback download if conversion fails.

5. **Retry and resiliency**
   - Retry transient errors (network timeouts).
   - Keep partial upload cleanup to avoid orphan files.

## Acceptance Criteria (Draft)
- Users see real-time upload status and explicit failure reasons.
- Virus rejections are visible and audited.
- Conversion state is visible and reliable for preview actions.
