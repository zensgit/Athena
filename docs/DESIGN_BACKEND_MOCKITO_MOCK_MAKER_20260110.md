# Design: Backend Mockito Mock Maker (2026-01-10)

## Goal
- Allow tests to run on JVMs that cannot self-attach for inline mocking.

## Approach
- Configure Mockito to use the subclass mock maker for tests.
- Add `mockito-extensions/org.mockito.plugins.MockMaker` under test resources.

## Files
- `ecm-core/src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker`
