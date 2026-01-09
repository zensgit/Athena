# Design: Backend Correspondent API Consistency Test (2026-01-08)

## Goal
- Validate that creating a correspondent is immediately reflected in the list endpoint response.

## Approach
- Add a controller test that posts a correspondent and then queries the list endpoint.
- Use a mocked service with an in-memory store to assert create + list consistency at the API layer.
- Configure the controller test with pageable argument resolver.

## Files
- `ecm-core/src/test/java/com/ecm/core/controller/CorrespondentControllerTest.java`
