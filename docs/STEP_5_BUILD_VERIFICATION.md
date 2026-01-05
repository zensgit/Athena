# Step 5 Verification: Build Checks

## Backend
- Command: `docker run --rm -v "$(pwd)":/workspace -w /workspace maven:3-eclipse-temurin-17 mvn -q -DskipTests compile`
- Result: ✅ Success

## Backend Tests
- Command: `docker run --rm -v "$(pwd)":/workspace -v "$HOME/.m2":/root/.m2 -w /workspace/ecm-core maven:3-eclipse-temurin-17 mvn test`
- Result: ✅ Success (Tests run: 21, Failures: 0, Errors: 0)
- Command (local): `cd ecm-core && mvn test`
- Result: ✅ Success (Tests run: 30, Failures: 0, Errors: 0)
- Note: `SearchAclElasticsearchTest` uses `ECM_ELASTICSEARCH_URL` or defaults to `http://localhost:9200` and skips if ES is unavailable.

## Backend Verify
- Command: `docker run --rm -v "$(pwd)":/workspace -v "$HOME/.m2":/root/.m2 -w /workspace/ecm-core maven:3-eclipse-temurin-17 mvn verify`
- Result: ✅ Success (Tests run: 21, Failures: 0, Errors: 0)

## Frontend
- Command: `npm run build`
- Result: ✅ Success (one Node deprecation warning about `fs.F_OK`)
