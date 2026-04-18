package com.ecm.core.controller;

import com.ecm.core.integration.ldap.LdapConnectionStatus;
import com.ecm.core.integration.ldap.LdapSyncResult;
import com.ecm.core.integration.ldap.LdapSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/ldap")
@RequiredArgsConstructor
@Tag(name = "LDAP Sync", description = "Admin endpoints for LDAP/AD connectivity and mirror sync")
@ConditionalOnProperty(name = "ecm.identity.provider", havingValue = "ldap")
public class LdapSyncController {

    private final LdapSyncService ldapSyncService;

    @PostMapping("/test-connection")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Test LDAP connection", description = "Bind to the configured LDAP/AD server and validate the configured base DNs")
    public ResponseEntity<LdapConnectionStatus> testConnection() {
        return ResponseEntity.ok(ldapSyncService.testConnection());
    }

    @PostMapping("/sync")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Run LDAP sync", description = "Synchronize LDAP/AD users, groups, and memberships into the local mirror tables")
    public ResponseEntity<LdapSyncResult> syncNow() {
        return ResponseEntity.ok(ldapSyncService.syncNow());
    }
}
