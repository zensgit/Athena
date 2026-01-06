# Verification: org.json Dependency Cleanup (2026-01-06)

- `cd ecm-core && mvn -DskipTests dependency:tree -Dincludes=org.json:json,com.vaadin.external.google:android-json`
- Result: only `org.json:json` remains on the dependency tree.
