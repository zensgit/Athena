package com.ecm.core.integration.ldap;

import java.util.List;

public record LdapDirectorySnapshot(
    List<LdapDirectoryUser> users,
    List<LdapDirectoryGroup> groups
) {
}
