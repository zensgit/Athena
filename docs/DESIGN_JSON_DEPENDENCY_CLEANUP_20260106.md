# Design: org.json Dependency Cleanup (2026-01-06)

## Goal
- Remove duplicate org.json implementations from the test classpath to avoid runtime ambiguity warnings.

## Approach
- Exclude `com.vaadin.external.google:android-json` from the Spring Boot test starter.
- Keep the runtime `org.json:json` dependency from OAuth/Oltu, so jsonassert continues to resolve org.json classes.

## Files
- ecm-core/pom.xml
