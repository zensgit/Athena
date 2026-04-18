package com.ecm.core.integration.ldap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ecm.identity.provider", havingValue = "ldap")
public class LdapSyncScheduler {

    private final LdapSyncService ldapSyncService;
    private final LdapSyncProperties properties;

    @Scheduled(cron = "${ecm.ldap.sync.cron:0 0 */4 * * *}")
    public void sync() {
        if (!properties.getSync().isEnabled()) {
            return;
        }
        LdapSyncResult result = ldapSyncService.runScheduledSync();
        log.info(
            "Scheduled LDAP sync completed with {} warnings and {} membership changes",
            result.warnings().size(),
            result.membershipsChanged()
        );
    }
}
