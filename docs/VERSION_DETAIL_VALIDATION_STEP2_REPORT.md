# Version Detail Validation - Step 2 Report (2025-12-23)

## Scope
- Extend API/DTO to expose more version metadata.
- Surface additional fields in UI for inspection.

## Backend Changes
- `ecm-core/src/main/java/com/ecm/core/dto/VersionDto.java`
  - Added fields: `mimeType`, `contentHash`, `contentId`, `status`.
  - DTO mapping updated from `Version` entity.

## Frontend Changes
- `ecm-frontend/src/types/index.ts`
  - `Version` now includes optional: `mimeType`, `contentHash`, `contentId`, `status`.
- `ecm-frontend/src/services/nodeService.ts`
  - Maps new fields from `ApiVersionResponse`.
- `ecm-frontend/src/components/dialogs/VersionHistoryDialog.tsx`
  - Version label tooltip shows mime type, hash, content id, status.

## Verification
### Backend Build
```
cd ecm-core
docker run --rm -v "$PWD":/workspace -w /workspace maven:3-eclipse-temurin-17 mvn -q -DskipTests compile
```
Result: PASS

### Frontend Lint
```
cd ecm-frontend
npm run lint
```
Result: PASS

## Notes
- Runtime API verification of new fields requires a backend restart to load updated DTOs.
