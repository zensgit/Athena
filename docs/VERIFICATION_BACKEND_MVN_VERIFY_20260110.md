# Verification: Backend mvn verify (2026-01-10)

## Command
- `mvn verify`
- `JAVA_TOOL_OPTIONS=-Djdk.attach.allowAttachSelf=true mvn verify`

## Result
- FAIL (Tests run: 81, Failures: 0, Errors: 74, Skipped: 0).
- Retry with `JAVA_TOOL_OPTIONS=-Djdk.attach.allowAttachSelf=true` still failed.

## Failure
- Mockito inline mock maker failed to initialize: `Could not self-attach to current VM using external process`.

## Follow-up
- Pass after switching to subclass mock maker: `docs/VERIFICATION_BACKEND_MVN_VERIFY_20260110_PASS.md`.

## Environment
- Java: 17 (Homebrew 17.0.17+0)
- OS: Mac OS X 26.2

## Artifacts
- Surefire reports: `ecm-core/target/surefire-reports/`
- Example failure: `ecm-core/target/surefire-reports/com.ecm.core.pipeline.processor.TikaTextExtractorTest.txt`
