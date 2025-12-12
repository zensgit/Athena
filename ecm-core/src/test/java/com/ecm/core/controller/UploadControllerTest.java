package com.ecm.core.controller;

import com.ecm.core.pipeline.PipelineResult;
import com.ecm.core.service.DocumentUploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UploadControllerTest {

    private MockMvc mockMvc;

    @Mock
    private DocumentUploadService uploadService;

    @InjectMocks
    private UploadController uploadController;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(uploadController).build();
    }

    @Test
    @DisplayName("Single upload returns 200 on success")
    void uploadShouldReturnOk() throws Exception {
        PipelineResult result = PipelineResult.builder()
            .success(true)
            .documentId(UUID.randomUUID())
            .contentId("content-1")
            .totalDurationMs(5)
            .errors(Map.of())
            .build();
        Mockito.when(uploadService.uploadDocument(Mockito.any(), Mockito.any(), Mockito.any()))
            .thenReturn(result);

        MockMultipartFile file = new MockMultipartFile(
            "file", "test.txt", MediaType.TEXT_PLAIN_VALUE, "hello".getBytes()
        );

        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/documents/upload")
                .file(file)
                .param("description", "unit test"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("Pipeline status endpoint returns 200")
    void pipelineStatusShouldReturnOk() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/documents/pipeline/status"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }
}
