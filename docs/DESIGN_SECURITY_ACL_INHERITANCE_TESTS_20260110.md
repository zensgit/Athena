# Design: Security ACL Inheritance Tests (2026-01-10)

## Goal
- Add regression coverage for ACL inheritance and explicit deny behavior in SecurityService.

## Approach
- Unit tests exercise child deny overriding parent allow.
- Unit tests verify inherit=false ignores parent permissions.
- Unit tests verify role-based allows are resolved from user repository roles.

## Impact
- Protects against regressions in ACL evaluation order and inheritance handling.

## Files
- ecm-core/src/test/java/com/ecm/core/service/SecurityServiceAclTest.java
