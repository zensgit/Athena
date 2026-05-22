package com.ecm.core.controller;

import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Node;
import com.ecm.core.service.FolderService;
import com.ecm.core.service.NodeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class FolderControllerResponseContractTest {

    @Mock
    private FolderService folderService;

    @Mock
    private NodeService nodeService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        FolderController controller = new FolderController(folderService, nodeService);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
            .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
            .build();
    }

    @Test
    @DisplayName("GET /folders/roots keeps FolderResponse queryCriteria as explicit null")
    void rootFoldersKeepNullQueryCriteriaInFolderResponse() throws Exception {
        Folder root = folder(
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            "Records",
            "/Records"
        );
        root.setQueryCriteria(null);

        when(folderService.getRootFolders()).thenReturn(List.of(root));

        mockMvc.perform(get("/api/v1/folders/roots"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(root.getId().toString()))
            .andExpect(jsonPath("$[0].name").value("Records"))
            .andExpect(jsonPath("$[0].description").value("Records description"))
            .andExpect(jsonPath("$[0].path").value("/Records"))
            .andExpect(jsonPath("$[0].parentId", nullValue()))
            .andExpect(jsonPath("$[0].folderType").value("GENERAL"))
            .andExpect(jsonPath("$[0].maxItems", nullValue()))
            .andExpect(jsonPath("$[0].allowedTypes", nullValue()))
            .andExpect(jsonPath("$[0].autoFileNaming").value(false))
            .andExpect(jsonPath("$[0].namingPattern", nullValue()))
            .andExpect(jsonPath("$[0].inheritPermissions").value(true))
            .andExpect(jsonPath("$[0].smart").value(false))
            .andExpect(jsonPath("$[0].queryCriteria", nullValue()))
            .andExpect(jsonPath("$[0].locked").value(false))
            .andExpect(jsonPath("$[0].lockedBy", nullValue()))
            .andExpect(jsonPath("$[0].createdBy").value("alice"))
            .andExpect(jsonPath("$[0].createdDate").value("2026-05-21T09:00:00"))
            .andExpect(jsonPath("$[0].lastModifiedBy").value("alice"))
            .andExpect(jsonPath("$[0].lastModifiedDate").value("2026-05-21T09:15:00"))
            .andExpect(jsonPath("$[0].size").doesNotExist());
    }

    @Test
    @DisplayName("GET /folders/{id}/contents keeps folder item size as explicit null")
    void folderContentsKeepFolderNodeResponseSizeAsExplicitNull() throws Exception {
        UUID folderId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        Folder childFolder = folder(
            UUID.fromString("33333333-3333-3333-3333-333333333333"),
            "Child",
            "/Records/Child"
        );

        when(folderService.getFolderContents(eq(folderId), any(Pageable.class))).thenReturn(
            new PageImpl<>(List.<Node>of(childFolder), PageRequest.of(0, 20), 1)
        );

        mockMvc.perform(get("/api/v1/folders/{folderId}/contents", folderId)
                .param("page", "0")
                .param("size", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].id").value(childFolder.getId().toString()))
            .andExpect(jsonPath("$.content[0].name").value("Child"))
            .andExpect(jsonPath("$.content[0].description").value("Child description"))
            .andExpect(jsonPath("$.content[0].path").value("/Records/Child"))
            .andExpect(jsonPath("$.content[0].nodeType").value("FOLDER"))
            .andExpect(jsonPath("$.content[0].parentId", nullValue()))
            .andExpect(jsonPath("$.content[0].size", nullValue()))
            .andExpect(jsonPath("$.content[0].contentType", nullValue()))
            .andExpect(jsonPath("$.content[0].isFolder").value(true))
            .andExpect(jsonPath("$.content[0].locked").value(false))
            .andExpect(jsonPath("$.content[0].lockedBy", nullValue()))
            .andExpect(jsonPath("$.content[0].createdBy").value("alice"))
            .andExpect(jsonPath("$.content[0].createdDate").value("2026-05-21T09:00:00"))
            .andExpect(jsonPath("$.content[0].lastModifiedBy").value("alice"))
            .andExpect(jsonPath("$.content[0].lastModifiedDate").value("2026-05-21T09:15:00"))
            .andExpect(jsonPath("$.content[0].queryCriteria").doesNotExist())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.totalPages").value(1))
            .andExpect(jsonPath("$.size").value(20))
            .andExpect(jsonPath("$.number").value(0))
            .andExpect(jsonPath("$.first").value(true))
            .andExpect(jsonPath("$.last").value(true))
            .andExpect(jsonPath("$.pageable").exists());
    }

    private static Folder folder(UUID id, String name, String path) {
        Folder folder = new Folder();
        folder.setId(id);
        folder.setName(name);
        folder.setDescription(name + " description");
        folder.setPath(path);
        folder.setCreatedBy("alice");
        folder.setCreatedDate(LocalDateTime.of(2026, 5, 21, 9, 0));
        folder.setLastModifiedBy("alice");
        folder.setLastModifiedDate(LocalDateTime.of(2026, 5, 21, 9, 15));
        folder.setInheritPermissions(true);
        return folder;
    }
}
