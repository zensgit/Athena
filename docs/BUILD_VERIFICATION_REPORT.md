# Build Verification Report

## Backend
- Command: `mvn -DskipTests compile` (Docker, Temurin 17)
- Result: BUILD SUCCESS
- Notes:
  - Lombok @Builder warnings in `SanityCheckReport.java` (initializers ignored without `@Builder.Default`).
  - `OdooService.java` uses unchecked operations (`-Xlint:unchecked` for details).

## Frontend
- Command: `npm run build`
- Result: Compiled successfully
- Notes:
  - Node warning: `DEP0176` (`fs.F_OK` deprecated).
