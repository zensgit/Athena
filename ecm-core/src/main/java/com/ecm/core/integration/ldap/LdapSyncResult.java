package com.ecm.core.integration.ldap;

import java.time.LocalDateTime;
import java.util.List;

public record LdapSyncResult(
    String trigger,
    LocalDateTime syncedAt,
    int usersCreated,
    int usersUpdated,
    int usersDisabled,
    int usersSkipped,
    int groupsCreated,
    int groupsUpdated,
    int groupsDisabled,
    int groupsSkipped,
    int membershipsChanged,
    int unresolvedMembers,
    List<String> warnings
) {
}
