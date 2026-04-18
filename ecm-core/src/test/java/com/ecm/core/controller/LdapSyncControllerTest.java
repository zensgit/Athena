package com.ecm.core.controller;

import com.ecm.core.integration.ldap.LdapConnectionStatus;
import com.ecm.core.integration.ldap.LdapSyncResult;
import com.ecm.core.integration.ldap.LdapSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class LdapSyncControllerTest {

    private MockMvc mockMvc;

    @Mock
    private LdapSyncService ldapSyncService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new LdapSyncController(ldapSyncService))
            .setControllerAdvice(new RestExceptionHandler())
            .build();
    }

    @Test
    @DisplayName("testConnection returns LDAP status payload")
    void testConnectionReturnsStatusPayload() throws Exception {
        Mockito.when(ldapSyncService.testConnection()).thenReturn(
            new LdapConnectionStatus(true, "ou=people,dc=example,dc=com", "ou=groups,dc=example,dc=com", "ok")
        );

        mockMvc.perform(post("/api/v1/admin/ldap/test-connection"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reachable").value(true))
            .andExpect(jsonPath("$.userBaseDn").value("ou=people,dc=example,dc=com"))
            .andExpect(jsonPath("$.message").value("ok"));
    }

    @Test
    @DisplayName("syncNow returns sync summary payload")
    void syncNowReturnsSyncSummary() throws Exception {
        Mockito.when(ldapSyncService.syncNow()).thenReturn(
            new LdapSyncResult(
                "manual",
                LocalDateTime.of(2026, 4, 14, 12, 0),
                2,
                1,
                0,
                0,
                1,
                0,
                0,
                0,
                3,
                1,
                List.of("warning")
            )
        );

        mockMvc.perform(post("/api/v1/admin/ldap/sync"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.trigger").value("manual"))
            .andExpect(jsonPath("$.usersCreated").value(2))
            .andExpect(jsonPath("$.membershipsChanged").value(3))
            .andExpect(jsonPath("$.warnings[0]").value("warning"));
    }
}
