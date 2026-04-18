package com.ecm.core.integration.ldap;

import java.util.Set;

public record LdapDirectoryGroup(
    String externalId,
    String name,
    String displayName,
    String description,
    String email,
    boolean enabled,
    String dn,
    Set<String> memberDns
) {
}
