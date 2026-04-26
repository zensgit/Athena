package com.ecm.core.controller;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.Node;
import com.ecm.core.integration.email.EmailIngestionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class EmailIntegrationControllerTest {

    @Mock
    private EmailIngestionService emailIngestionService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(objectMapper);

        mockMvc = MockMvcBuilders
            .standaloneSetup(new EmailIntegrationController(emailIngestionService))
            .setMessageConverters(converter)
            .setControllerAdvice(new RestExceptionHandler())
            .build();
    }

    // ------------------------------------------------------------------ helpers

    private Document stubDocument(UUID docId, String name) {
        Document doc = new Document();
        doc.setId(docId);
        doc.setName(name);
        doc.setMimeType("message/rfc822");
        doc.setArchiveStatus(Node.ArchiveStatus.LIVE);
        doc.setStatus(Node.NodeStatus.ACTIVE);
        return doc;
    }

    // ------------------------------------------------------------------ tests

    @Test
    @DisplayName("POST /ingest without folderId ingests email and returns document")
    void ingestEmailWithoutFolderIdReturnsDocument() throws Exception {
        UUID docId = UUID.randomUUID();
        Document doc = stubDocument(docId, "Subject: Test.eml");
        Mockito.when(emailIngestionService.ingestEmail(any(MultipartFile.class), isNull()))
            .thenReturn(doc);

        mockMvc.perform(multipart("/api/v1/integration/email/ingest")
                .file(new MockMultipartFile("file", "test.eml", "message/rfc822",
                    "Subject: Test".getBytes())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(docId.toString()));
    }

    @Test
    @DisplayName("POST /ingest with folderId routes email into specified folder")
    void ingestEmailWithFolderIdReturnsDocument() throws Exception {
        UUID docId = UUID.randomUUID();
        UUID folderId = UUID.randomUUID();
        Document doc = stubDocument(docId, "Invoice.eml");
        Mockito.when(emailIngestionService.ingestEmail(any(MultipartFile.class), eq(folderId)))
            .thenReturn(doc);

        mockMvc.perform(multipart("/api/v1/integration/email/ingest")
                .file(new MockMultipartFile("file", "invoice.eml", "message/rfc822",
                    "Subject: Invoice".getBytes()))
                .param("folderId", folderId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(docId.toString()));
    }

    @Test
    @DisplayName("POST /ingest with text/plain content type is accepted by the service")
    void ingestEmailWithTextPlainContentTypeStillSucceeds() throws Exception {
        UUID docId = UUID.randomUUID();
        Document doc = stubDocument(docId, "plain.eml");
        Mockito.when(emailIngestionService.ingestEmail(any(MultipartFile.class), isNull()))
            .thenReturn(doc);

        mockMvc.perform(multipart("/api/v1/integration/email/ingest")
                .file(new MockMultipartFile("file", "plain.eml", "text/plain",
                    "Subject: Test".getBytes())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(docId.toString()));
    }
}
