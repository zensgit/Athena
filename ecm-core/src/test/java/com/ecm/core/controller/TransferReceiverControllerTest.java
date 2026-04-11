package com.ecm.core.controller;

import com.ecm.core.entity.ReplicationDefinition;
import com.ecm.core.service.transfer.TransferReceiverHeaders;
import com.ecm.core.service.transfer.TransferReceiverService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TransferReceiverControllerTest {

    @Mock
    private TransferReceiverService transferReceiverService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TransferReceiverController controller = new TransferReceiverController(transferReceiverService);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
            .setControllerAdvice(new RestExceptionHandler())
            .build();
    }

    @Test
    @DisplayName("GET /transfer/receiver/verify returns verified folder payload")
    void verifyFolderReturnsVerifiedFolderPayload() throws Exception {
        UUID folderId = UUID.randomUUID();
        when(transferReceiverService.verifyFolder(folderId, "replicator", "top-secret"))
            .thenReturn(new TransferReceiverService.VerifyFolderResponse(folderId, "Outbound"));

        mockMvc.perform(get("/api/v1/transfer/receiver/verify")
                .param("folderId", folderId.toString())
                .header(TransferReceiverHeaders.USER_HEADER, "replicator")
                .header(TransferReceiverHeaders.SECRET_HEADER, "top-secret"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.folderId").value(folderId.toString()))
            .andExpect(jsonPath("$.folderName").value("Outbound"));
    }

    @Test
    @DisplayName("POST /transfer/receiver/folders creates folder with custom auth headers")
    void createFolderCreatesFolderWithCustomAuthHeaders() throws Exception {
        UUID parentFolderId = UUID.randomUUID();
        UUID folderId = UUID.randomUUID();
        when(transferReceiverService.createFolder(
            eq(new TransferReceiverService.CreateFolderRequest(
                parentFolderId,
                "Contracts",
                "Replica",
                ReplicationDefinition.ConflictPolicy.SKIP
            )),
            eq(null),
            eq("shared-secret")
        )).thenReturn(new TransferReceiverService.CreateFolderResponse(
            folderId,
            "Contracts",
            TransferReceiverService.ConflictDisposition.SKIPPED,
            "Skipped existing receiver folder"
        ));

        mockMvc.perform(post("/api/v1/transfer/receiver/folders")
                .contentType(MediaType.APPLICATION_JSON)
                .header(TransferReceiverHeaders.SECRET_HEADER, "shared-secret")
                .content("""
                    {
                      "parentFolderId": "%s",
                      "name": "Contracts",
                      "description": "Replica",
                      "conflictPolicy": "SKIP"
                    }
                    """.formatted(parentFolderId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.folderId").value(folderId.toString()))
            .andExpect(jsonPath("$.folderName").value("Contracts"))
            .andExpect(jsonPath("$.disposition").value("SKIPPED"))
            .andExpect(jsonPath("$.message").value("Skipped existing receiver folder"));
    }

    @Test
    @DisplayName("POST /transfer/receiver/documents uploads multipart document")
    void uploadDocumentUploadsMultipartDocument() throws Exception {
        UUID parentFolderId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile("file", "contract.pdf", "application/pdf", "pdf".getBytes());
        when(transferReceiverService.uploadDocument(
            anyMultipart(),
            eq(parentFolderId),
            eq("Signed"),
            eq(ReplicationDefinition.ConflictPolicy.OVERWRITE),
            eq("replicator"),
            eq("top-secret")
        )).thenReturn(new TransferReceiverService.UploadDocumentResponse(
            documentId,
            "contract.pdf",
            TransferReceiverService.ConflictDisposition.OVERWRITTEN,
            "Overwrote receiver document"
        ));

        mockMvc.perform(multipart("/api/v1/transfer/receiver/documents")
                .file(file)
                .param("parentFolderId", parentFolderId.toString())
                .param("description", "Signed")
                .param("conflictPolicy", "OVERWRITE")
                .header(TransferReceiverHeaders.USER_HEADER, "replicator")
                .header(TransferReceiverHeaders.SECRET_HEADER, "top-secret"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.documentId").value(documentId.toString()))
            .andExpect(jsonPath("$.documentName").value("contract.pdf"))
            .andExpect(jsonPath("$.disposition").value("OVERWRITTEN"))
            .andExpect(jsonPath("$.message").value("Overwrote receiver document"));
    }

    @Test
    @DisplayName("receiver endpoints surface forbidden transfer credential failures")
    void receiverEndpointsSurfaceForbiddenCredentialFailures() throws Exception {
        UUID folderId = UUID.randomUUID();
        when(transferReceiverService.verifyFolder(folderId, null, "wrong"))
            .thenThrow(new SecurityException("Transfer receiver credentials do not permit folder: " + folderId));

        mockMvc.perform(get("/api/v1/transfer/receiver/verify")
                .param("folderId", folderId.toString())
                .header(TransferReceiverHeaders.SECRET_HEADER, "wrong"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").value("Transfer receiver credentials do not permit folder: " + folderId));
    }

    private static MultipartFile anyMultipart() {
        return org.mockito.ArgumentMatchers.any(MultipartFile.class);
    }
}
