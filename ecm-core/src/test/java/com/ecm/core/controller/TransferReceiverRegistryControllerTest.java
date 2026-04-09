package com.ecm.core.controller;

import com.ecm.core.entity.TransferReceiverRegistration;
import com.ecm.core.entity.TransferTarget;
import com.ecm.core.service.TransferReceiverRegistryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TransferReceiverRegistryControllerTest {

    @Mock
    private TransferReceiverRegistryService transferReceiverRegistryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TransferReceiverRegistryController controller = new TransferReceiverRegistryController(transferReceiverRegistryService);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
            .setControllerAdvice(new RestExceptionHandler())
            .build();
    }

    @Test
    @DisplayName("GET /transfer/receivers lists receiver registrations with diagnostics")
    void listReceiversReturnsRegistrations() throws Exception {
        when(transferReceiverRegistryService.listReceivers()).thenReturn(List.of(receiverDto(
            UUID.randomUUID(),
            "receiver-east",
            TransferTarget.AuthType.BEARER,
            TransferTarget.VerificationStatus.VERIFIED,
            TransferReceiverRegistration.AccessStatus.SUCCESS
        )));

        mockMvc.perform(get("/api/v1/transfer/receivers"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("receiver-east"))
            .andExpect(jsonPath("$[0].authType").value("BEARER"))
            .andExpect(jsonPath("$[0].verificationStatus").value("VERIFIED"))
            .andExpect(jsonPath("$[0].lastAccessStatus").value("SUCCESS"));
    }

    @Test
    @DisplayName("POST /transfer/receivers creates receiver registration")
    void createReceiverReturnsCreatedRegistration() throws Exception {
        UUID rootFolderId = UUID.randomUUID();
        when(transferReceiverRegistryService.createReceiver(any())).thenReturn(receiverDto(
            UUID.randomUUID(),
            "receiver-east",
            TransferTarget.AuthType.BASIC,
            TransferTarget.VerificationStatus.NEVER_VERIFIED,
            TransferReceiverRegistration.AccessStatus.NEVER_USED
        ));

        mockMvc.perform(post("/api/v1/transfer/receivers")
                .contentType("application/json")
                .content("""
                    {
                      "name": "receiver-east",
                      "description": "Inbound east region",
                      "rootFolderId": "%s",
                      "authType": "BASIC",
                      "authUsername": "replicator",
                      "authSecret": "top-secret",
                      "enabled": true
                    }
                    """.formatted(rootFolderId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("receiver-east"))
            .andExpect(jsonPath("$.authType").value("BASIC"))
            .andExpect(jsonPath("$.verificationStatus").value("NEVER_VERIFIED"));
    }

    @Test
    @DisplayName("POST /transfer/receivers/{id}/verify returns verification diagnostics")
    void verifyReceiverReturnsDiagnostics() throws Exception {
        UUID receiverId = UUID.randomUUID();
        when(transferReceiverRegistryService.verifyReceiver(receiverId)).thenReturn(receiverDto(
            receiverId,
            "receiver-east",
            TransferTarget.AuthType.BEARER,
            TransferTarget.VerificationStatus.VERIFIED,
            TransferReceiverRegistration.AccessStatus.SUCCESS
        ));

        mockMvc.perform(post("/api/v1/transfer/receivers/{receiverId}/verify", receiverId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(receiverId.toString()))
            .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"))
            .andExpect(jsonPath("$.verificationMessage").value("Verified receiver root folder: Inbound Root"));
    }

    @Test
    @DisplayName("PUT /transfer/receivers/{id} updates receiver registration")
    void updateReceiverReturnsUpdatedRegistration() throws Exception {
        UUID receiverId = UUID.randomUUID();
        UUID rootFolderId = UUID.randomUUID();
        when(transferReceiverRegistryService.updateReceiver(any(), any())).thenReturn(receiverDto(
            receiverId,
            "receiver-east",
            TransferTarget.AuthType.BEARER,
            TransferTarget.VerificationStatus.NEVER_VERIFIED,
            TransferReceiverRegistration.AccessStatus.NEVER_USED
        ));

        mockMvc.perform(put("/api/v1/transfer/receivers/{receiverId}", receiverId)
                .contentType("application/json")
                .content("""
                    {
                      "name": "receiver-east",
                      "description": "Updated",
                      "rootFolderId": "%s",
                      "authType": "BEARER",
                      "authSecret": "shared-secret",
                      "enabled": false
                    }
                    """.formatted(rootFolderId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(receiverId.toString()))
            .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    @DisplayName("DELETE /transfer/receivers/{id} returns no content")
    void deleteReceiverReturnsNoContent() throws Exception {
        UUID receiverId = UUID.randomUUID();
        doNothing().when(transferReceiverRegistryService).deleteReceiver(receiverId);

        mockMvc.perform(delete("/api/v1/transfer/receivers/{receiverId}", receiverId))
            .andExpect(status().isNoContent());
    }

    private TransferReceiverRegistryService.TransferReceiverDto receiverDto(
        UUID id,
        String name,
        TransferTarget.AuthType authType,
        TransferTarget.VerificationStatus verificationStatus,
        TransferReceiverRegistration.AccessStatus lastAccessStatus
    ) {
        LocalDateTime now = LocalDateTime.now();
        return new TransferReceiverRegistryService.TransferReceiverDto(
            id,
            name,
            "Inbound east region",
            UUID.randomUUID(),
            "Inbound Root",
            authType,
            authType == TransferTarget.AuthType.BASIC ? "replicator" : null,
            authType != TransferTarget.AuthType.NONE,
            true,
            verificationStatus,
            verificationStatus == TransferTarget.VerificationStatus.VERIFIED
                ? "Verified receiver root folder: Inbound Root"
                : null,
            verificationStatus == TransferTarget.VerificationStatus.VERIFIED ? now : null,
            lastAccessStatus,
            lastAccessStatus == TransferReceiverRegistration.AccessStatus.SUCCESS ? "Verified receiver folder access: Inbound Root" : null,
            lastAccessStatus == TransferReceiverRegistration.AccessStatus.NEVER_USED ? null : now,
            now.minusHours(1),
            now
        );
    }
}
