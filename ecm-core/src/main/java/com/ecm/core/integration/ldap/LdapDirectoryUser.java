package com.ecm.core.integration.ldap;

public record LdapDirectoryUser(
    String externalId,
    String username,
    String email,
    String firstName,
    String lastName,
    String displayName,
    String department,
    String jobTitle,
    boolean enabled,
    String dn
) {
}
