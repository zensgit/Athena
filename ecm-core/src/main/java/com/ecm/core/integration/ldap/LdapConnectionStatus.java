package com.ecm.core.integration.ldap;

public record LdapConnectionStatus(
    boolean reachable,
    String userBaseDn,
    String groupBaseDn,
    String message
) {
}
