package com.ecm.core.integration.ldap;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "ecm.ldap")
public class LdapSyncProperties {

    private String url = "ldap://localhost:389";

    private String bindDn;

    private String bindPassword;

    private String userBaseDn;

    private String userFilter = "(objectClass=person)";

    private String userUsernameAttribute = "uid";

    private String userEmailAttribute = "mail";

    private String userFirstNameAttribute = "givenName";

    private String userLastNameAttribute = "sn";

    private String userDisplayNameAttribute = "displayName";

    private String userDepartmentAttribute = "department";

    private String userJobTitleAttribute = "title";

    private String userExternalIdAttribute = "entryUUID";

    private String userEnabledAttribute;

    private String groupBaseDn;

    private String groupFilter = "(|(objectClass=groupOfNames)(objectClass=group)(objectClass=groupOfUniqueNames))";

    private String groupNameAttribute = "cn";

    private String groupDisplayNameAttribute = "displayName";

    private String groupDescriptionAttribute = "description";

    private String groupEmailAttribute = "mail";

    private String groupExternalIdAttribute = "entryUUID";

    private String groupMemberAttribute = "member";

    private int connectTimeoutMs = 5000;

    private int readTimeoutMs = 10000;

    private Sync sync = new Sync();

    @Getter
    @Setter
    public static class Sync {
        private boolean enabled = false;
        private String cron = "0 0 */4 * * *";
    }
}
