package com.ecm.core.integration.ldap;

public interface LdapDirectoryClient {

    LdapConnectionStatus testConnection();

    LdapDirectorySnapshot fetchSnapshot();
}
