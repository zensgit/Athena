# Folder Contents contentType - Verification

Date: 2025-12-22

## Automated Checks
- `mvn -DskipTests compile` (not run: `mvn` not installed locally; Docker access blocked in this session).

## Manual Verification Steps
1. Call `GET /api/v1/folders/{id}/contents`.
2. Confirm document items include `contentType` (e.g., `application/pdf`).
3. Confirm folder items either omit `contentType` or return null.

## Notes
- Compile should be re-run in an environment with Maven or Docker access.
