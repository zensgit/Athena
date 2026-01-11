package com.ecm.core.controller;

import com.ecm.core.integration.antivirus.AntivirusService;
import com.ecm.core.integration.wopi.model.WopiHealthResponse;
import com.ecm.core.integration.wopi.service.WopiEditorService;
import com.ecm.core.ml.MLServiceClient;
import com.ecm.core.repository.DocumentRepository;
import com.ecm.core.search.FullTextSearchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SystemStatusController.class)
@ContextConfiguration(classes = {
    SystemStatusController.class,
    SystemStatusControllerSecurityTest.TestSecurityConfig.class
})
class SystemStatusControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DocumentRepository documentRepository;

    @MockBean
    private ConnectionFactory rabbitConnectionFactory;

    @MockBean
    private FullTextSearchService fullTextSearchService;

    @MockBean
    private MLServiceClient mlServiceClient;

    @MockBean
    private WopiEditorService wopiEditorService;

    @MockBean
    private AntivirusService antivirusService;

    @MockBean
    private RestTemplate restTemplate;

    @Configuration
    @EnableWebSecurity
    @EnableMethodSecurity(prePostEnabled = true)
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/api/**").authenticated()
                    .anyRequest().permitAll()
                )
                .httpBasic(basic -> {});
            return http.build();
        }
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("System status requires admin role")
    void systemStatusRequiresAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/system/status"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can access system status")
    void systemStatusAllowsAdmin() throws Exception {
        Mockito.when(fullTextSearchService.getIndexStats())
            .thenReturn(Map.of("documentCount", 0L));
        Mockito.when(mlServiceClient.getModelInfo())
            .thenReturn(MLServiceClient.ModelInfo.empty());
        Mockito.when(wopiEditorService.getHealth())
            .thenReturn(WopiHealthResponse.builder().enabled(false).build());
        Mockito.when(antivirusService.isEnabled()).thenReturn(false);

        mockMvc.perform(get("/api/v1/system/status"))
            .andExpect(status().isOk());
    }
}
